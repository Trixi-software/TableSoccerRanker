<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api/client';
	import type { User, TeamSuggestion, MatchCreateRequest, Match, MatchPlayer } from '$lib/api/types';

	let users: User[] = $state([]);
	let step: 1 | 2 | 3 | 4 = $state(1);

	// Step 1: selected player IDs
	let selectedIds: string[] = $state([]);

	// Step 2: team suggestion (editable)
	let suggestion: TeamSuggestion | null = $state(null);
	let yellowAttacker: User | null = $state(null);
	let yellowDefender: User | null = $state(null);
	let whiteAttacker: User | null = $state(null);
	let whiteDefender: User | null = $state(null);

	// Step 3: win probability
	let yellowProb: number | null = $state(null);
	let whiteProb: number | null = $state(null);

	// Step 4: score + ELO preview
	let yellowScore: number = $state(0);
	let whiteScore: number = $state(0);
	let saving = $state(false);
	let previewData: Match | null = $state(null);
	let previewTimer: ReturnType<typeof setTimeout> | null = $state(null);
	let previewLoading = $state(false);

	// Derived: does at least one team have 10 goals?
	let hasValidScore = $derived(yellowScore >= 10 || whiteScore >= 10);

	onMount(async () => {
		users = await api.get<User[]>('/api/users');
	});

	function togglePlayer(id: string) {
		if (selectedIds.includes(id)) {
			selectedIds = selectedIds.filter(p => p !== id);
		} else if (selectedIds.length < 4) {
			selectedIds = [...selectedIds, id];
		}
	}

	async function suggestTeams() {
		suggestion = await api.post<TeamSuggestion>('/api/teams/suggest', { playerIds: selectedIds });
		yellowAttacker = suggestion.yellowAttacker;
		yellowDefender = suggestion.yellowDefender;
		whiteAttacker = suggestion.whiteAttacker;
		whiteDefender = suggestion.whiteDefender;
		step = 2;
	}

	function swapPlayers(pos1: string, pos2: string) {
		const players: Record<string, User | null> = {
			ya: yellowAttacker, yd: yellowDefender,
			wa: whiteAttacker, wd: whiteDefender
		};
		const temp = players[pos1];
		players[pos1] = players[pos2];
		players[pos2] = temp;
		yellowAttacker = players.ya;
		yellowDefender = players.yd;
		whiteAttacker = players.wa;
		whiteDefender = players.wd;
	}

	async function confirmTeams() {
		step = 3;
		// Load win probability via preview with 0:0 score
		await loadProbability();
	}

	async function loadProbability() {
		if (!yellowAttacker || !yellowDefender || !whiteAttacker || !whiteDefender) return;
		try {
			const preview = await api.post<Match>('/api/matches/preview', {
				yellowAttacker: yellowAttacker.id,
				yellowDefender: yellowDefender.id,
				whiteAttacker: whiteAttacker.id,
				whiteDefender: whiteDefender.id,
				yellowScore: 0,
				whiteScore: 0
			});
			const yp = preview.players.find(p => p.teamColor === 'YELLOW');
			const wp = preview.players.find(p => p.teamColor === 'WHITE');
			yellowProb = yp?.winProbability ?? null;
			whiteProb = wp?.winProbability ?? null;
		} catch (e) {
			yellowProb = null;
			whiteProb = null;
		}
	}

	function startScoring() {
		step = 4;
		previewData = null;
	}

	// Debounced ELO preview — fires 2s after score stops changing, only if score is valid
	function onScoreChange() {
		if (previewTimer) clearTimeout(previewTimer);
		previewData = null;
		if (yellowScore < 10 && whiteScore < 10) return;
		previewTimer = setTimeout(() => loadPreview(), 2000);
	}

	async function loadPreview() {
		if (!yellowAttacker || !yellowDefender || !whiteAttacker || !whiteDefender) return;
		if (yellowScore < 10 && whiteScore < 10) return;
		previewLoading = true;
		try {
			previewData = await api.post<Match>('/api/matches/preview', {
				yellowAttacker: yellowAttacker.id,
				yellowDefender: yellowDefender.id,
				whiteAttacker: whiteAttacker.id,
				whiteDefender: whiteDefender.id,
				yellowScore,
				whiteScore
			});
		} catch (e) {
			previewData = null;
		}
		previewLoading = false;
	}

	function getPreviewPlayer(teamColor: 'YELLOW' | 'WHITE', role: 'ATTACKER' | 'DEFENDER'): MatchPlayer | undefined {
		return previewData?.players.find(p => p.teamColor === teamColor && p.playerRole === role);
	}

	function setScore(yellow: number, white: number) {
		yellowScore = yellow;
		whiteScore = white;
		onScoreChange();
	}

	function changeYellow(delta: number) {
		yellowScore = Math.max(0, yellowScore + delta);
		onScoreChange();
	}

	function changeWhite(delta: number) {
		whiteScore = Math.max(0, whiteScore + delta);
		onScoreChange();
	}

	async function saveMatch() {
		if (!yellowAttacker || !yellowDefender || !whiteAttacker || !whiteDefender) return;
		saving = true;
		try {
			const request: MatchCreateRequest = {
				yellowAttacker: yellowAttacker.id,
				yellowDefender: yellowDefender.id,
				whiteAttacker: whiteAttacker.id,
				whiteDefender: whiteDefender.id,
				yellowScore,
				whiteScore
			};
			await api.post('/api/matches', request);
			goto('/');
		} finally {
			saving = false;
		}
	}
</script>

<div class="space-y-6">
	<!-- Step indicator -->
	<div class="flex items-center gap-2">
		{#each [1, 2, 3, 4] as s}
			<div class="flex items-center gap-2 {s <= step ? 'text-blue-600' : 'text-gray-300'}">
				<div class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
					{s === step ? 'bg-blue-600 text-white' : s < step ? 'bg-blue-100 text-blue-600' : 'bg-gray-100 text-gray-400'}">
					{s}
				</div>
				{#if s < 4}
					<div class="w-8 h-0.5 {s < step ? 'bg-blue-600' : 'bg-gray-200'}"></div>
				{/if}
			</div>
		{/each}
	</div>

	<!-- Step 1: Select Players -->
	{#if step === 1}
		<div>
			<h1 class="text-2xl font-bold text-gray-900 mb-1">Select 4 Players</h1>
			<p class="text-gray-500 text-sm mb-4">Choose who's playing the next match</p>

			<div class="grid grid-cols-2 gap-3">
				{#each users as user}
					<button
						onclick={() => togglePlayer(user.id)}
						class="flex items-center gap-3 p-3 rounded-xl border-2 transition-all text-left
							{selectedIds.includes(user.id) ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'}"
						disabled={selectedIds.length >= 4 && !selectedIds.includes(user.id)}
					>
						{#if user.avatarUrl}
							<img src={user.avatarUrl} alt={user.displayName} class="w-10 h-10 rounded-full" />
						{:else}
							<div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-sm font-bold">
								{user.displayName?.[0] ?? '?'}
							</div>
						{/if}
						<div class="min-w-0">
							<p class="font-medium text-gray-900 truncate text-sm">{user.displayName}</p>
							<p class="text-xs text-gray-500">ELO {user.eloRating}</p>
						</div>
					</button>
				{/each}
			</div>

			<button
				onclick={suggestTeams}
				disabled={selectedIds.length !== 4}
				class="w-full mt-4 py-3 rounded-xl font-medium text-white transition-colors
					{selectedIds.length === 4 ? 'bg-blue-600 hover:bg-blue-700' : 'bg-gray-300 cursor-not-allowed'}"
			>
				Suggest Teams ({selectedIds.length}/4)
			</button>
		</div>
	{/if}

	<!-- Step 2: Team Suggestion -->
	{#if step === 2}
		<div>
			<h1 class="text-2xl font-bold text-gray-900 mb-1">Team Suggestion</h1>
			<p class="text-gray-500 text-sm mb-4">Balanced by ranking. Tap swap to adjust.</p>

			<div class="grid grid-cols-2 gap-4">
				<!-- Yellow Team -->
				<div class="bg-yellow-50 border-2 border-yellow-200 rounded-xl p-4">
					<div class="flex items-center gap-2 mb-3">
						<div class="w-4 h-4 rounded-full bg-yellow-400"></div>
						<span class="font-bold text-yellow-800">Yellow</span>
					</div>
					{#if yellowAttacker}
						<div class="space-y-2">
							<div class="bg-white rounded-lg p-2">
								<p class="text-[10px] text-gray-400 uppercase">Attacker</p>
								<p class="font-medium text-sm">{yellowAttacker.displayName}</p>
							</div>
							<div class="bg-white rounded-lg p-2">
								<p class="text-[10px] text-gray-400 uppercase">Defender</p>
								<p class="font-medium text-sm">{yellowDefender?.displayName}</p>
							</div>
						</div>
					{/if}
				</div>

				<!-- White Team -->
				<div class="bg-gray-50 border-2 border-gray-200 rounded-xl p-4">
					<div class="flex items-center gap-2 mb-3">
						<div class="w-4 h-4 rounded-full bg-gray-300 border border-gray-400"></div>
						<span class="font-bold text-gray-700">White</span>
					</div>
					{#if whiteAttacker}
						<div class="space-y-2">
							<div class="bg-white rounded-lg p-2">
								<p class="text-[10px] text-gray-400 uppercase">Attacker</p>
								<p class="font-medium text-sm">{whiteAttacker.displayName}</p>
							</div>
							<div class="bg-white rounded-lg p-2">
								<p class="text-[10px] text-gray-400 uppercase">Defender</p>
								<p class="font-medium text-sm">{whiteDefender?.displayName}</p>
							</div>
						</div>
					{/if}
				</div>
			</div>

			<div class="flex gap-2 mt-4">
				<button onclick={() => swapPlayers('ya', 'wa')} class="flex-1 py-2 rounded-lg border border-gray-300 text-sm hover:bg-gray-50">Swap Attackers</button>
				<button onclick={() => swapPlayers('yd', 'wd')} class="flex-1 py-2 rounded-lg border border-gray-300 text-sm hover:bg-gray-50">Swap Defenders</button>
			</div>
			<div class="flex gap-2 mt-2">
				<button onclick={() => swapPlayers('ya', 'yd')} class="flex-1 py-2 rounded-lg border border-gray-300 text-sm hover:bg-gray-50">Swap Yellow Roles</button>
				<button onclick={() => swapPlayers('wa', 'wd')} class="flex-1 py-2 rounded-lg border border-gray-300 text-sm hover:bg-gray-50">Swap White Roles</button>
			</div>

			<div class="flex gap-3 mt-4">
				<button onclick={() => step = 1} class="flex-1 py-3 rounded-xl border border-gray-300 font-medium hover:bg-gray-50">Back</button>
				<button onclick={confirmTeams} class="flex-1 py-3 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700">Confirm Teams</button>
			</div>
		</div>
	{/if}

	<!-- Step 3: Play -->
	{#if step === 3}
		<div class="text-center">
			<h1 class="text-2xl font-bold text-gray-900 mb-2">Match In Progress</h1>
			<p class="text-gray-500 mb-6">Good luck! Record the score when the match is over.</p>

			<div class="grid grid-cols-2 gap-4 mb-4">
				<div class="bg-yellow-50 border-2 border-yellow-200 rounded-xl p-4">
					<div class="w-4 h-4 rounded-full bg-yellow-400 mx-auto mb-2"></div>
					<p class="font-medium text-sm">{yellowAttacker?.displayName}</p>
					<p class="font-medium text-sm">{yellowDefender?.displayName}</p>
				</div>
				<div class="bg-gray-50 border-2 border-gray-200 rounded-xl p-4">
					<div class="w-4 h-4 rounded-full bg-gray-300 border border-gray-400 mx-auto mb-2"></div>
					<p class="font-medium text-sm">{whiteAttacker?.displayName}</p>
					<p class="font-medium text-sm">{whiteDefender?.displayName}</p>
				</div>
			</div>

			<!-- Win probability -->
			{#if yellowProb != null && whiteProb != null}
				<div class="bg-white rounded-xl shadow-sm p-4 mb-6">
					<p class="text-xs text-gray-500 mb-2 uppercase font-semibold">Win Probability</p>
					<div class="flex items-center gap-3">
						<span class="text-lg font-bold text-yellow-700 w-16 text-right">{yellowProb.toFixed(1)}%</span>
						<div class="flex-1 h-3 bg-gray-100 rounded-full overflow-hidden flex">
							<div class="bg-yellow-400 h-full transition-all" style="width: {yellowProb}%"></div>
							<div class="bg-gray-400 h-full transition-all" style="width: {whiteProb}%"></div>
						</div>
						<span class="text-lg font-bold text-gray-600 w-16">{whiteProb.toFixed(1)}%</span>
					</div>
				</div>
			{/if}

			<div class="flex gap-3">
				<button onclick={() => step = 2} class="flex-1 py-3 rounded-xl border border-gray-300 font-medium hover:bg-gray-50">Back</button>
				<button onclick={startScoring} class="flex-1 py-3 rounded-xl bg-green-600 text-white font-medium hover:bg-green-700">Enter Score</button>
			</div>
		</div>
	{/if}

	<!-- Step 4: Record Score -->
	{#if step === 4}
		<div>
			<h1 class="text-2xl font-bold text-gray-900 mb-4 text-center">Record Score</h1>

			<!-- Quick score buttons -->
			<div class="flex justify-center gap-2 mb-4">
				<button onclick={() => setScore(10, 0)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">10:0</button>
				<button onclick={() => setScore(10, 5)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">10:5</button>
				<button onclick={() => setScore(10, 8)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">10:8</button>
				<button onclick={() => setScore(10, 9)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">10:9</button>
				<button onclick={() => setScore(5, 10)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">5:10</button>
				<button onclick={() => setScore(0, 10)} class="px-3 py-1.5 rounded-lg border border-gray-200 text-xs font-medium hover:bg-gray-50">0:10</button>
			</div>

			<div class="grid grid-cols-2 gap-6">
				<!-- Yellow score -->
				<div class="text-center">
					<div class="flex items-center gap-2 justify-center mb-3">
						<div class="w-4 h-4 rounded-full bg-yellow-400"></div>
						<span class="font-bold text-yellow-800">Yellow</span>
					</div>
					<div class="flex items-center justify-center gap-3">
						<button onclick={() => changeYellow(-1)}
							class="w-12 h-12 rounded-full bg-gray-100 text-2xl font-bold hover:bg-gray-200 flex items-center justify-center">-</button>
						<span class="text-5xl font-bold tabular-nums w-16 text-center">{yellowScore}</span>
						<button onclick={() => changeYellow(1)}
							class="w-12 h-12 rounded-full bg-gray-100 text-2xl font-bold hover:bg-gray-200 flex items-center justify-center">+</button>
					</div>
				</div>

				<!-- White score -->
				<div class="text-center">
					<div class="flex items-center gap-2 justify-center mb-3">
						<div class="w-4 h-4 rounded-full bg-gray-300 border border-gray-400"></div>
						<span class="font-bold text-gray-700">White</span>
					</div>
					<div class="flex items-center justify-center gap-3">
						<button onclick={() => changeWhite(-1)}
							class="w-12 h-12 rounded-full bg-gray-100 text-2xl font-bold hover:bg-gray-200 flex items-center justify-center">-</button>
						<span class="text-5xl font-bold tabular-nums w-16 text-center">{whiteScore}</span>
						<button onclick={() => changeWhite(1)}
							class="w-12 h-12 rounded-full bg-gray-100 text-2xl font-bold hover:bg-gray-200 flex items-center justify-center">+</button>
					</div>
				</div>
			</div>

			<!-- ELO Preview -->
			{#if previewLoading}
				<div class="text-center text-gray-400 text-xs mt-4">Calculating ELO...</div>
			{:else if previewData && hasValidScore}
				{@const yAtt = getPreviewPlayer('YELLOW', 'ATTACKER')}
				{@const yDef = getPreviewPlayer('YELLOW', 'DEFENDER')}
				{@const wAtt = getPreviewPlayer('WHITE', 'ATTACKER')}
				{@const wDef = getPreviewPlayer('WHITE', 'DEFENDER')}
				<div class="mt-4 bg-gray-50 rounded-xl p-3 border border-gray-100">
					<p class="text-[10px] text-gray-500 uppercase font-semibold text-center mb-2">ELO Preview</p>
					<div class="grid grid-cols-2 gap-4 text-sm">
						<div class="space-y-1">
							{#each [yAtt, yDef] as p}
								{#if p}
									<div class="flex items-center justify-between">
										<span class="text-gray-700 truncate">{p.displayName}</span>
										<span class="font-mono font-bold {(p.eloChange ?? 0) >= 0 ? 'text-green-600' : 'text-red-500'}">
											{(p.eloChange ?? 0) >= 0 ? '+' : ''}{p.eloChange}
										</span>
									</div>
								{/if}
							{/each}
						</div>
						<div class="space-y-1">
							{#each [wAtt, wDef] as p}
								{#if p}
									<div class="flex items-center justify-between">
										<span class="text-gray-700 truncate">{p.displayName}</span>
										<span class="font-mono font-bold {(p.eloChange ?? 0) >= 0 ? 'text-green-600' : 'text-red-500'}">
											{(p.eloChange ?? 0) >= 0 ? '+' : ''}{p.eloChange}
										</span>
									</div>
								{/if}
							{/each}
						</div>
					</div>
					{#if yAtt?.winProbability != null}
						<p class="text-[10px] text-gray-400 text-center mt-2">
							P: {yAtt.winProbability.toFixed(1)}% - {wAtt?.winProbability?.toFixed(1)}%
						</p>
					{/if}
				</div>
			{/if}

			<div class="flex gap-3 mt-6">
				<button onclick={() => step = 3} class="flex-1 py-3 rounded-xl border border-gray-300 font-medium hover:bg-gray-50">Back</button>
				<button onclick={saveMatch} disabled={saving || !hasValidScore}
					class="flex-1 py-3 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:bg-gray-300">
					{saving ? 'Saving...' : 'Save Match'}
				</button>
			</div>
		</div>
	{/if}
</div>
