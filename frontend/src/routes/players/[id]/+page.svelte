<script lang="ts">
	import { page } from '$app/stores';
	import { onMount } from 'svelte';
	import { api } from '$lib/api/client';
	import type { PlayerStats, User, PlayerEloTimeline, EloDataPoint } from '$lib/api/types';
	import BludistakIcon from '$lib/components/ui/BludistakIcon.svelte';

	let playerId = $derived($page.params.id);
	let player: User | null = $state(null);
	let stats: PlayerStats | null = $state(null);
	let allDataPoints: EloDataPoint[] = $state([]);
	let loadError: string | null = $state(null);

	// Chart state
	type Period = '30d' | '90d' | '365d' | 'all';
	let period: Period = $state('all');
	let chartW = $state(600);
	let chartH = $state(300);
	const periodLabels: Record<Period, string> = { '30d': '30d', '90d': '90d', '365d': '1y', 'all': 'All' };

	let useWeeks = $derived(period === '365d' || period === 'all');

	// Filter data by period
	let filteredPoints = $derived(() => {
		if (period === 'all') return allDataPoints;
		const days = period === '30d' ? 30 : period === '90d' ? 90 : 365;
		const cutoff = new Date(Date.now() - days * 86400000).toISOString();
		return allDataPoints.filter(p => p.playedAt >= cutoff);
	});

	// Week helper
	function getMonday(dateStr: string): string {
		const d = new Date(dateStr + 'T00:00:00');
		const day = d.getDay();
		d.setDate(d.getDate() - day + (day === 0 ? -6 : 1));
		return d.toISOString().split('T')[0];
	}

	// Bucket points by day or week
	let bucketedPoints = $derived(() => {
		const pts = filteredPoints();
		const byBucket = new Map<string, number>();
		for (const p of pts) {
			const day = new Date(p.playedAt).toISOString().split('T')[0];
			const bucket = useWeeks ? getMonday(day) : day;
			byBucket.set(bucket, p.eloAfter);
		}
		return [...byBucket.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([bucket, elo]) => ({ bucket, elo }));
	});

	let chartPoints = $derived(bucketedPoints());
	let minElo = $derived(chartPoints.length > 0 ? Math.min(...chartPoints.map(p => p.elo)) - 25 : 990);
	let maxElo = $derived(chartPoints.length > 0 ? Math.max(...chartPoints.map(p => p.elo)) + 25 : 1010);
	let eloRange = $derived(maxElo - minElo || 1);

	// Layout
	const padL = 50, padR = 12, padT = 12, padB = 32;
	let plotW = $derived(chartW - padL - padR);
	let plotH = $derived(chartH - padT - padB);

	function xPos(i: number) { return padL + (i / Math.max(chartPoints.length - 1, 1)) * plotW; }
	function yPos(elo: number) { return padT + ((maxElo - elo) / eloRange) * plotH; }

	let yGridLines = $derived(() => {
		const range = maxElo - minElo;
		let step = range > 600 ? 200 : range > 300 ? 100 : range > 120 ? 50 : range > 50 ? 20 : 10;
		const lines: number[] = [];
		for (let v = Math.ceil(minElo / step) * step; v <= maxElo; v += step) lines.push(v);
		return lines;
	});

	let xLabels = $derived(() => {
		if (chartPoints.length <= 10) return chartPoints.map((p, i) => ({ i, label: p.bucket }));
		const step = Math.ceil(chartPoints.length / 8);
		return chartPoints
			.filter((_, i) => i % step === 0 || i === chartPoints.length - 1)
			.map(p => ({ i: chartPoints.indexOf(p), label: p.bucket }));
	});

	function formatLabel(bucket: string): string {
		const d = new Date(bucket + 'T00:00:00');
		if (useWeeks || period === '90d') return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
		return bucket.slice(5);
	}

	// Gradient fill path
	let areaPath = $derived(() => {
		if (chartPoints.length < 2) return '';
		const bottom = padT + plotH;
		let d = `M${xPos(0)},${bottom}`;
		chartPoints.forEach((p, i) => { d += ` L${xPos(i)},${yPos(p.elo)}`; });
		d += ` L${xPos(chartPoints.length - 1)},${bottom} Z`;
		return d;
	});

	let linePath = $derived(() => {
		if (chartPoints.length < 2) return '';
		return chartPoints.map((p, i) => `${i === 0 ? 'M' : 'L'}${xPos(i)},${yPos(p.elo)}`).join(' ');
	});

	// Tooltip
	let hoverIdx: number | null = $state(null);

	onMount(async () => {
		try {
			const [p, s, timelines] = await Promise.all([
				api.get<User>(`/api/users/${playerId}`),
				api.get<PlayerStats>(`/api/stats/player/${playerId}`),
				api.get<PlayerEloTimeline[]>('/api/rankings/elo-timeline')
			]);
			player = p;
			stats = s;
			const tl = timelines.find(t => t.userId === playerId);
			allDataPoints = tl?.dataPoints ?? [];
		} catch (e) {
			loadError = 'Failed to load player profile. Please try again.';
		}
	});
</script>

{#if loadError}
	<div class="bg-red-50 text-red-700 rounded-lg p-4 text-sm">{loadError}</div>
{:else if !stats || !player}
	<div class="text-center py-12 text-gray-400">Loading...</div>
{:else}
	<div class="space-y-6">
		<!-- Header -->
		<div class="bg-white rounded-xl shadow-sm p-4 sm:p-6">
			<div class="flex items-center gap-3 sm:gap-4">
				{#if player.avatarUrl}
					<img src={player.avatarUrl} alt={player.displayName} class="w-12 h-12 sm:w-16 sm:h-16 rounded-full shrink-0" />
				{:else}
					<div class="w-12 h-12 sm:w-16 sm:h-16 rounded-full bg-gray-200 flex items-center justify-center text-xl sm:text-2xl font-bold text-gray-500 shrink-0">
						{player.displayName?.[0] ?? '?'}
					</div>
				{/if}
				<div class="flex-1 min-w-0">
					<div class="flex items-center gap-2">
						<h1 class="text-xl sm:text-2xl font-bold text-gray-900 truncate">{player.displayName}</h1>
						{#if stats.bludistakWins > 0}
							<div class="flex items-center gap-1 shrink-0">
								<BludistakIcon size={28} />
								<span class="text-sm font-bold text-amber-700">{stats.bludistakWins}x</span>
							</div>
						{/if}
					</div>
					<div class="flex items-center gap-2 sm:gap-3 text-xs sm:text-sm text-gray-500 mt-0.5">
						<span>ELO <strong class="text-gray-900">{player.eloRating}</strong></span>
						<span class="text-gray-300">|</span>
						<span>ATT <strong class="text-blue-600">{stats.attackerElo}</strong></span>
						<span class="text-gray-300">|</span>
						<span>DEF <strong class="text-green-600">{stats.defenderElo}</strong></span>
					</div>
				</div>
			</div>
		</div>

		<!-- Quick stats -->
		<div class="grid grid-cols-2 md:grid-cols-4 gap-3">
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-gray-900">{stats.totalMatches}</p>
				<p class="text-xs text-gray-500">Matches</p>
			</div>
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-green-600">{stats.winRate}%</p>
				<p class="text-xs text-gray-500">Win Rate</p>
			</div>
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-gray-900">{stats.wins}W {stats.losses}L {stats.draws}D</p>
				<p class="text-xs text-gray-500">Record</p>
			</div>
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-gray-900">{stats.avgGoalsScoredPerMatch}</p>
				<p class="text-xs text-gray-500">Avg Goals/Match</p>
			</div>
		</div>

		<!-- Streaks & Biggest Win -->
		<div class="grid grid-cols-2 md:grid-cols-4 gap-3">
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-green-600">{stats.longestWinStreak}</p>
				<p class="text-xs text-gray-500">Longest Win Streak</p>
			</div>
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				<p class="text-2xl font-bold text-red-500">{stats.longestLoseStreak}</p>
				<p class="text-xs text-gray-500">Longest Lose Streak</p>
			</div>
			{#if stats.biggestWin}
				<div class="bg-white rounded-xl shadow-sm p-4 text-center">
					<p class="text-2xl font-bold text-gray-900">{stats.biggestWin.description}</p>
					<p class="text-xs text-gray-500">Biggest Win (+{stats.biggestWin.goalDiff})</p>
				</div>
			{/if}
			<div class="bg-white rounded-xl shadow-sm p-4 text-center">
				{#if stats.currentStreak.type === 'WIN'}
					<p class="text-2xl font-bold text-green-600">{stats.currentStreak.count}W</p>
				{:else if stats.currentStreak.type === 'LOSE'}
					<p class="text-2xl font-bold text-red-500">{stats.currentStreak.count}L</p>
				{:else}
					<p class="text-2xl font-bold text-gray-400">-</p>
				{/if}
				<p class="text-xs text-gray-500">Current Streak</p>
			</div>
		</div>

		<!-- ELO Stats -->
		{#if stats.highestEloEver != null}
			<div class="grid grid-cols-2 md:grid-cols-4 gap-3">
				<div class="bg-white rounded-xl shadow-sm p-4 text-center">
					<p class="text-2xl font-bold text-green-600">{stats.highestEloEver}</p>
					<p class="text-xs text-gray-500">Highest ELO</p>
				</div>
				<div class="bg-white rounded-xl shadow-sm p-4 text-center">
					<p class="text-2xl font-bold text-red-500">{stats.lowestEloEver}</p>
					<p class="text-xs text-gray-500">Lowest ELO</p>
				</div>
				<div class="bg-white rounded-xl shadow-sm p-4 text-center">
					<p class="text-2xl font-bold text-green-600">+{stats.biggestEloGain}</p>
					<p class="text-xs text-gray-500">Best ELO Gain</p>
				</div>
				<div class="bg-white rounded-xl shadow-sm p-4 text-center">
					<p class="text-2xl font-bold text-red-500">{stats.biggestEloLoss}</p>
					<p class="text-xs text-gray-500">Worst ELO Loss</p>
				</div>
			</div>
		{/if}

		<!-- Form (last 10) -->
		<div class="bg-white rounded-xl shadow-sm p-4">
			<h2 class="font-semibold text-gray-900 mb-3">Recent Form</h2>
			<div class="flex gap-1.5">
				{#each stats.recentForm as entry}
					<div class="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold
						{entry.won ? 'bg-green-100 text-green-700' : entry.goalDiff === 0 ? 'bg-gray-100 text-gray-500' : 'bg-red-100 text-red-700'}">
						{entry.won ? 'W' : entry.goalDiff === 0 ? 'D' : 'L'}
					</div>
				{:else}
					<p class="text-gray-400 text-sm">No matches yet</p>
				{/each}
			</div>
		</div>

		<!-- ELO Progression Chart -->
		{#if allDataPoints.length > 1}
			<div class="bg-white rounded-xl shadow-sm p-4">
				<div class="flex items-center justify-between gap-2 mb-4">
					<h2 class="font-semibold text-gray-900 shrink-0">ELO Progression</h2>
					<div class="flex gap-0.5 bg-gray-100 rounded-lg p-0.5">
						{#each (['30d', '90d', '365d', 'all'] as const) as p}
							<button
								onclick={() => period = p}
								class="py-1 px-2 sm:px-2.5 rounded-md text-xs font-medium transition-all
									{period === p ? 'bg-white shadow-sm text-gray-900' : 'text-gray-500 hover:text-gray-700'}"
							>
								{periodLabels[p]}
							</button>
						{/each}
					</div>
				</div>

				{#if chartPoints.length < 2}
					<p class="text-gray-400 text-sm text-center py-8">Not enough data for this period.</p>
				{:else}
					{@const currentElo = chartPoints[chartPoints.length - 1].elo}
					{@const eloDiff = chartPoints[chartPoints.length - 1].elo - chartPoints[0].elo}
					<!-- Current ELO indicator -->
					<div class="flex items-baseline gap-2 mb-3">
						<span class="text-2xl font-bold text-blue-600">{currentElo}</span>
						<span class="text-sm font-medium {eloDiff >= 0 ? 'text-green-600' : 'text-red-500'}">
							{eloDiff >= 0 ? '+' : ''}{eloDiff} in this period
						</span>
					</div>

					<div
						class="player-chart-area"
						bind:clientWidth={chartW}
						bind:clientHeight={chartH}
						role="img"
						aria-label="ELO Progression Chart"
					>
						<svg viewBox="0 0 {chartW} {chartH}" width="100%" height="100%"
							onmouseleave={() => hoverIdx = null}
						>
							<defs>
								<linearGradient id="eloFill" x1="0" y1="0" x2="0" y2="1">
									<stop offset="0%" stop-color="#3b82f6" stop-opacity="0.15" />
									<stop offset="100%" stop-color="#3b82f6" stop-opacity="0.02" />
								</linearGradient>
							</defs>

							<!-- Y grid -->
							{#each yGridLines() as elo}
								<line x1={padL} y1={yPos(elo)} x2={chartW - padR} y2={yPos(elo)} stroke="#f3f4f6" stroke-width="1" />
								<text x={padL - 6} y={yPos(elo) + 4} fill="#9ca3af" font-size="10" text-anchor="end" font-family="monospace">{elo}</text>
							{/each}

							<!-- X labels -->
							{#each xLabels() as { i, label }}
								<text x={xPos(i)} y={chartH - 6} fill="#9ca3af" font-size="9" text-anchor="middle">{formatLabel(label)}</text>
							{/each}

							<!-- Gradient fill area -->
							<path d={areaPath()} fill="url(#eloFill)" />

							<!-- Line -->
							<path d={linePath()} fill="none" stroke="#3b82f6" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />

							<!-- Hover hit areas (invisible wide rects for each point) -->
							{#each chartPoints as p, i}
								{@const x = xPos(i)}
								{@const halfGap = plotW / Math.max(chartPoints.length - 1, 1) / 2}
								<rect
									x={x - halfGap} y={padT} width={halfGap * 2} height={plotH}
									fill="transparent"
									onmouseenter={() => hoverIdx = i}
								/>
							{/each}

							<!-- Hover crosshair + tooltip -->
							{#if hoverIdx !== null && chartPoints[hoverIdx]}
								{@const hp = chartPoints[hoverIdx]}
								{@const hx = xPos(hoverIdx)}
								{@const hy = yPos(hp.elo)}
								<!-- Vertical line -->
								<line x1={hx} y1={padT} x2={hx} y2={padT + plotH} stroke="#3b82f6" stroke-width="1" stroke-dasharray="3,3" opacity="0.4" />
								<!-- Dot -->
								<circle cx={hx} cy={hy} r="5" fill="#3b82f6" stroke="white" stroke-width="2" />
								<!-- Tooltip background -->
								{@const tooltipText = `${hp.elo}  ${formatLabel(hp.bucket)}`}
								{@const tx = Math.min(Math.max(hx, padL + 40), chartW - padR - 40)}
								<rect x={tx - 38} y={hy - 30} width="76" height="22" rx="4" fill="#1f2937" opacity="0.9" />
								<text x={tx} y={hy - 15} fill="white" font-size="10" text-anchor="middle" font-weight="600">{hp.elo}</text>
								<rect x={tx - 32} y={hy - 52} width="64" height="18" rx="4" fill="#1f2937" opacity="0.7" />
								<text x={tx} y={hy - 39} fill="#d1d5db" font-size="9" text-anchor="middle">{formatLabel(hp.bucket)}</text>
							{/if}

							<!-- Static dots (small, for visual anchor) -->
							{#each chartPoints as p, i}
								{#if hoverIdx !== i}
									<circle cx={xPos(i)} cy={yPos(p.elo)} r="2.5" fill="#3b82f6" opacity="0.6" />
								{/if}
							{/each}
						</svg>
					</div>
				{/if}
			</div>
		{/if}

		<!-- Role and Color stats -->
		<div class="grid md:grid-cols-2 gap-4">
			<div class="bg-white rounded-xl shadow-sm p-4">
				<h2 class="font-semibold text-gray-900 mb-3">Role Performance</h2>
				<div class="space-y-3">
					<div>
						<div class="flex justify-between text-sm mb-1">
							<span class="text-gray-600">Attacker <span class="text-blue-600 font-mono text-xs">ELO {stats.attackerElo}</span></span>
							<span class="font-medium">{stats.attackerStats.winRate.toFixed(0)}% win ({stats.attackerStats.matches})</span>
						</div>
						<div class="h-2 bg-gray-100 rounded-full overflow-hidden">
							<div class="h-full bg-blue-500 rounded-full" style="width: {stats.attackerStats.winRate}%"></div>
						</div>
					</div>
					<div>
						<div class="flex justify-between text-sm mb-1">
							<span class="text-gray-600">Defender <span class="text-green-600 font-mono text-xs">ELO {stats.defenderElo}</span></span>
							<span class="font-medium">{stats.defenderStats.winRate.toFixed(0)}% win ({stats.defenderStats.matches})</span>
						</div>
						<div class="h-2 bg-gray-100 rounded-full overflow-hidden">
							<div class="h-full bg-green-500 rounded-full" style="width: {stats.defenderStats.winRate}%"></div>
						</div>
					</div>
				</div>
			</div>

			<div class="bg-white rounded-xl shadow-sm p-4">
				<h2 class="font-semibold text-gray-900 mb-3">Team Color Performance</h2>
				<div class="space-y-3">
					<div>
						<div class="flex justify-between text-sm mb-1">
							<span class="flex items-center gap-1.5"><span class="w-3 h-3 rounded-full bg-yellow-400"></span> Yellow</span>
							<span class="font-medium">{stats.yellowStats.winRate.toFixed(0)}% win ({stats.yellowStats.matches})</span>
						</div>
						<div class="h-2 bg-gray-100 rounded-full overflow-hidden">
							<div class="h-full bg-yellow-400 rounded-full" style="width: {stats.yellowStats.winRate}%"></div>
						</div>
					</div>
					<div>
						<div class="flex justify-between text-sm mb-1">
							<span class="flex items-center gap-1.5"><span class="w-3 h-3 rounded-full bg-gray-300 border border-gray-400"></span> White</span>
							<span class="font-medium">{stats.whiteStats.winRate.toFixed(0)}% win ({stats.whiteStats.matches})</span>
						</div>
						<div class="h-2 bg-gray-100 rounded-full overflow-hidden">
							<div class="h-full bg-gray-400 rounded-full" style="width: {stats.whiteStats.winRate}%"></div>
						</div>
					</div>
				</div>
			</div>
		</div>

		<!-- Partner/Opponent stats -->
		<div class="grid md:grid-cols-2 gap-4">
			{#if stats.bestPartner}
				<div class="bg-white rounded-xl shadow-sm p-4">
					<h2 class="font-semibold text-gray-900 mb-2">Best Partner</h2>
					<p class="text-lg font-medium">{stats.bestPartner.displayName}</p>
					<p class="text-sm text-gray-500">{stats.bestPartner.winRate}% win rate ({stats.bestPartner.matches} matches)</p>
				</div>
			{/if}
			{#if stats.nemesis}
				<div class="bg-white rounded-xl shadow-sm p-4">
					<h2 class="font-semibold text-gray-900 mb-2">Nemesis</h2>
					<p class="text-lg font-medium">{stats.nemesis.displayName}</p>
					<p class="text-sm text-gray-500">{stats.nemesis.lossRate}% loss rate ({stats.nemesis.matches} matches)</p>
				</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.player-chart-area {
		width: 100%;
		height: 280px;
		min-height: 200px;
	}
	@media (min-height: 700px) {
		.player-chart-area { height: 320px; }
	}
	@media (min-height: 900px) {
		.player-chart-area { height: 380px; }
	}
</style>
