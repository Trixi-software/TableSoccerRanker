<script lang="ts">
	import type { PlayerRanking } from '$lib/api/types';

	let { rankings, scoreLabel = 'Score' }: { rankings: PlayerRanking[]; scoreLabel?: string } = $props();
</script>

<div class="bg-white rounded-xl shadow-sm overflow-hidden">
	<!-- Desktop table -->
	<table class="w-full hidden md:table">
		<thead>
			<tr class="border-b border-gray-100">
				<th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">#</th>
				<th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Player</th>
				<th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">{scoreLabel}</th>
			</tr>
		</thead>
		<tbody>
			{#each rankings as player, i}
				<tr class="border-b border-gray-50 hover:bg-gray-50 transition-colors">
					<td class="px-4 py-3">
						<span class="text-sm font-bold {i === 0 ? 'text-yellow-500' : i === 1 ? 'text-gray-400' : i === 2 ? 'text-amber-600' : 'text-gray-500'}">
							{player.rank}
						</span>
					</td>
					<td class="px-4 py-3">
						<a href="/players/{player.userId}" class="flex items-center gap-3 hover:underline">
							{#if player.avatarUrl}
								<img src={player.avatarUrl} alt={player.displayName} class="w-8 h-8 rounded-full" />
							{:else}
								<div class="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-xs font-bold text-gray-600">
									{player.displayName?.[0] ?? '?'}
								</div>
							{/if}
							<span class="font-medium text-gray-900">{player.displayName}</span>
						</a>
					</td>
					<td class="px-4 py-3 text-right">
						<span class="font-mono font-semibold text-gray-900">{Math.round(player.score)}</span>
					</td>
				</tr>
			{:else}
				<tr>
					<td colspan="3" class="px-4 py-8 text-center text-gray-500">No rankings yet</td>
				</tr>
			{/each}
		</tbody>
	</table>

	<!-- Mobile cards -->
	<div class="md:hidden divide-y divide-gray-50">
		{#each rankings as player, i}
			<a href="/players/{player.userId}" class="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors">
				<span class="w-8 text-center text-sm font-bold {i === 0 ? 'text-yellow-500' : i === 1 ? 'text-gray-400' : i === 2 ? 'text-amber-600' : 'text-gray-500'}">
					{player.rank}
				</span>
				{#if player.avatarUrl}
					<img src={player.avatarUrl} alt={player.displayName} class="w-10 h-10 rounded-full" />
				{:else}
					<div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-sm font-bold text-gray-600">
						{player.displayName?.[0] ?? '?'}
					</div>
				{/if}
				<div class="flex-1 min-w-0">
					<p class="font-medium text-gray-900 truncate">{player.displayName}</p>
				</div>
				<span class="font-mono font-semibold text-gray-900">{Math.round(player.score)}</span>
			</a>
		{:else}
			<div class="px-4 py-8 text-center text-gray-500">No rankings yet</div>
		{/each}
	</div>
</div>
