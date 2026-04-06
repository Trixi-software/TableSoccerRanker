<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '$lib/api/client';
	import type { Match, Page, User } from '$lib/api/types';
	import MatchCard from '$lib/components/match/MatchCard.svelte';

	let allMatches: Match[] = $state([]);
	let users: User[] = $state([]);
	let loading = $state(true);
	let filtersOpen = $state(false);

	// Filters
	let filterPlayer = $state('');
	let filterPosition = $state<'' | 'ATTACKER' | 'DEFENDER'>('');
	let filterTeammate = $state('');
	let filterMinElo = $state('');
	let filterMaxElo = $state('');
	let filterMinProb = $state('');
	let filterMaxProb = $state('');

	// Sort
	type SortKey = 'date_desc' | 'date_asc' | 'elo_desc' | 'elo_asc' | 'prob_desc' | 'prob_asc' | 'score_desc';
	let sortKey: SortKey = $state('date_desc');

	const sortLabels: Record<SortKey, string> = {
		'date_desc': 'Newest first',
		'date_asc': 'Oldest first',
		'elo_desc': 'ELO change (high)',
		'elo_asc': 'ELO change (low)',
		'prob_desc': 'Win prob. (high)',
		'prob_asc': 'Win prob. (low)',
		'score_desc': 'Score diff (big)',
	};

	// Pagination
	const PAGE_SIZE = 20;
	let pageNum = $state(0);

	// Available players for teammate filter (only those who played with filterPlayer)
	let availableTeammates = $derived(() => {
		if (!filterPlayer) return [];
		const mates = new Set<string>();
		for (const m of allMatches) {
			const fp = m.players.find(p => p.userId === filterPlayer);
			if (!fp) continue;
			for (const p of m.players) {
				if (p.userId !== filterPlayer && p.teamColor === fp.teamColor) {
					mates.add(p.userId);
				}
			}
		}
		return users.filter(u => mates.has(u.id));
	});

	let hasActiveFilters = $derived(
		filterPlayer !== '' || filterPosition !== '' || filterTeammate !== '' ||
		filterMinElo !== '' || filterMaxElo !== '' || filterMinProb !== '' || filterMaxProb !== ''
	);

	// Apply filters
	let filteredMatches = $derived(() => {
		let result = allMatches;

		if (filterPlayer) {
			result = result.filter(m => {
				const fp = m.players.find(p => p.userId === filterPlayer);
				if (!fp) return false;
				if (filterPosition && fp.playerRole !== filterPosition) return false;
				if (filterTeammate) {
					const mate = m.players.find(p => p.userId === filterTeammate && p.teamColor === fp.teamColor);
					if (!mate) return false;
				}
				return true;
			});
		}

		if (filterMinElo || filterMaxElo) {
			const min = filterMinElo ? parseInt(filterMinElo) : -Infinity;
			const max = filterMaxElo ? parseInt(filterMaxElo) : Infinity;
			result = result.filter(m => {
				if (filterPlayer) {
					const fp = m.players.find(p => p.userId === filterPlayer);
					if (!fp || fp.eloChange == null) return false;
					return fp.eloChange >= min && fp.eloChange <= max;
				}
				return m.players.some(p => p.eloChange != null && p.eloChange >= min && p.eloChange <= max);
			});
		}

		if (filterMinProb || filterMaxProb) {
			const min = filterMinProb ? parseFloat(filterMinProb) : 0;
			const max = filterMaxProb ? parseFloat(filterMaxProb) : 100;
			result = result.filter(m => {
				if (filterPlayer) {
					const fp = m.players.find(p => p.userId === filterPlayer);
					if (!fp || fp.winProbability == null) return false;
					return fp.winProbability >= min && fp.winProbability <= max;
				}
				return m.players.some(p => p.winProbability != null && p.winProbability >= min && p.winProbability <= max);
			});
		}

		return result;
	});

	// Sort helper: get a sort value for a match
	function getSortValue(m: Match, key: SortKey): number {
		if (key === 'date_desc' || key === 'date_asc') {
			return new Date(m.playedAt).getTime();
		}
		if (key === 'score_desc') {
			return Math.abs(m.yellowScore - m.whiteScore);
		}
		// For ELO/prob sorts, use filterPlayer's value if set, otherwise max across all players
		const relevantPlayer = filterPlayer
			? m.players.find(p => p.userId === filterPlayer)
			: null;
		if (key === 'elo_desc' || key === 'elo_asc') {
			if (relevantPlayer) return relevantPlayer.eloChange ?? 0;
			return Math.max(...m.players.map(p => Math.abs(p.eloChange ?? 0)));
		}
		if (key === 'prob_desc' || key === 'prob_asc') {
			if (relevantPlayer) return relevantPlayer.winProbability ?? 50;
			return Math.max(...m.players.map(p => p.winProbability ?? 50));
		}
		return 0;
	}

	// Apply sort
	let sortedMatches = $derived(() => {
		const filtered = filteredMatches();
		const sorted = [...filtered].sort((a, b) => {
			const va = getSortValue(a, sortKey);
			const vb = getSortValue(b, sortKey);
			const asc = sortKey.endsWith('_asc');
			return asc ? va - vb : vb - va;
		});
		return sorted;
	});

	// Paginate
	let totalPages = $derived(Math.ceil(sortedMatches().length / PAGE_SIZE));
	let pagedMatches = $derived(() => {
		const start = pageNum * PAGE_SIZE;
		return sortedMatches().slice(start, start + PAGE_SIZE);
	});

	// Reset page when filters/sort change
	$effect(() => {
		// Touch reactive deps
		filterPlayer; filterPosition; filterTeammate;
		filterMinElo; filterMaxElo; filterMinProb; filterMaxProb; sortKey;
		pageNum = 0;
	});

	function clearFilters() {
		filterPlayer = '';
		filterPosition = '';
		filterTeammate = '';
		filterMinElo = '';
		filterMaxElo = '';
		filterMinProb = '';
		filterMaxProb = '';
	}

	let loadError: string | null = $state(null);

	onMount(async () => {
		try {
			const [matchData, userData] = await Promise.all([
				api.get<Page<Match>>('/api/matches?size=5000'),
				api.get<User[]>('/api/users')
			]);
			allMatches = matchData.content;
			users = userData;
		} catch (e) {
			loadError = 'Failed to load matches. Please try again.';
		} finally {
			loading = false;
		}
	});
</script>

<div class="space-y-4">
	<div class="flex items-center justify-between">
		<h1 class="text-2xl font-bold text-gray-900">Match History</h1>
		<a href="/next-match" class="px-4 py-2 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700">
			New Match
		</a>
	</div>

	{#if loading}
		<div class="text-center py-12 text-gray-400">Loading...</div>
	{:else if loadError}
		<div class="bg-red-50 text-red-700 rounded-lg p-4 text-sm">{loadError}</div>
	{:else}
		<!-- Filter/Sort bar -->
		<div class="bg-white rounded-xl shadow-sm p-3 space-y-3">
			<div class="flex items-center gap-3 flex-wrap">
				<!-- Sort -->
				<div class="flex items-center gap-1.5">
					<span class="text-xs text-gray-500">Sort:</span>
					<select bind:value={sortKey}
						class="text-sm border border-gray-200 rounded-lg px-2 py-1.5 bg-white text-gray-700">
						{#each Object.entries(sortLabels) as [key, label]}
							<option value={key}>{label}</option>
						{/each}
					</select>
				</div>

				<!-- Filter toggle -->
				<button
					onclick={() => filtersOpen = !filtersOpen}
					class="flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-lg border transition-colors
						{hasActiveFilters ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'}"
				>
					<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
							d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
					</svg>
					Filters
					{#if hasActiveFilters}
						<span class="w-2 h-2 rounded-full bg-blue-500"></span>
					{/if}
				</button>

				{#if hasActiveFilters}
					<button onclick={clearFilters}
						class="text-xs text-gray-500 hover:text-gray-700 underline">
						Clear all
					</button>
				{/if}

				<!-- Results count -->
				<span class="text-xs text-gray-400 ml-auto">
					{sortedMatches().length} match{sortedMatches().length !== 1 ? 'es' : ''}
				</span>
			</div>

			<!-- Expandable filters -->
			{#if filtersOpen}
				<div class="border-t border-gray-100 pt-3 grid grid-cols-2 md:grid-cols-3 gap-3">
					<!-- Player -->
					<div>
						<label class="text-[11px] font-medium text-gray-500 uppercase mb-1 block">Player</label>
						<select bind:value={filterPlayer}
							class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5 bg-white">
							<option value="">All players</option>
							{#each users as u}
								<option value={u.id}>{u.displayName}</option>
							{/each}
						</select>
					</div>

					<!-- Position -->
					<div>
						<label class="text-[11px] font-medium text-gray-500 uppercase mb-1 block">Position</label>
						<select bind:value={filterPosition} disabled={!filterPlayer}
							class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5 bg-white disabled:opacity-40">
							<option value="">Any</option>
							<option value="ATTACKER">Attacker</option>
							<option value="DEFENDER">Defender</option>
						</select>
					</div>

					<!-- Teammate -->
					<div>
						<label class="text-[11px] font-medium text-gray-500 uppercase mb-1 block">Teammate</label>
						<select bind:value={filterTeammate} disabled={!filterPlayer}
							class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5 bg-white disabled:opacity-40">
							<option value="">Any</option>
							{#each availableTeammates() as u}
								<option value={u.id}>{u.displayName}</option>
							{/each}
						</select>
					</div>

					<!-- ELO change range -->
					<div>
						<label class="text-[11px] font-medium text-gray-500 uppercase mb-1 block">ELO Change</label>
						<div class="flex gap-1.5">
							<input type="number" bind:value={filterMinElo} placeholder="Min"
								class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5" />
							<input type="number" bind:value={filterMaxElo} placeholder="Max"
								class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5" />
						</div>
					</div>

					<!-- Win probability range -->
					<div>
						<label class="text-[11px] font-medium text-gray-500 uppercase mb-1 block">Win Prob. %</label>
						<div class="flex gap-1.5">
							<input type="number" bind:value={filterMinProb} placeholder="Min" min="0" max="100"
								class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5" />
							<input type="number" bind:value={filterMaxProb} placeholder="Max" min="0" max="100"
								class="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5" />
						</div>
					</div>
				</div>
			{/if}
		</div>

		<!-- Match list -->
		<div class="space-y-3">
			{#each pagedMatches() as match}
				<MatchCard {match} highlightPlayer={filterPlayer || undefined} />
			{:else}
				<div class="bg-white rounded-xl shadow-sm p-12 text-center text-gray-500">
					{hasActiveFilters ? 'No matches match your filters' : 'No matches recorded yet'}
				</div>
			{/each}
		</div>

		<!-- Pagination -->
		{#if totalPages > 1}
			<div class="flex justify-center gap-2 pt-4">
				<button onclick={() => pageNum = Math.max(0, pageNum - 1)} disabled={pageNum === 0}
					class="px-3 py-2 rounded-lg border border-gray-300 text-sm disabled:opacity-50 hover:bg-gray-50">
					Previous
				</button>
				<span class="px-3 py-2 text-sm text-gray-600">
					{pageNum + 1} / {totalPages}
				</span>
				<button onclick={() => pageNum = Math.min(totalPages - 1, pageNum + 1)} disabled={pageNum >= totalPages - 1}
					class="px-3 py-2 rounded-lg border border-gray-300 text-sm disabled:opacity-50 hover:bg-gray-50">
					Next
				</button>
			</div>
		{/if}
	{/if}
</div>
