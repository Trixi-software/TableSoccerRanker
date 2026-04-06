<script lang="ts">
	import { page } from '$app/stores';
	import { invalidateAll } from '$app/navigation';

	let currentPath = $derived($page.url.pathname);
	let user = $derived($page.data.user);
	let menuOpen = $state(false);

	const navLinks = [
		{ href: '/', label: 'Rankings' },
		{ href: '/next-match', label: 'Next Match' },
		{ href: '/matches', label: 'Matches' },
		{ href: '/stats', label: 'Stats' }
	];

	async function logout() {
		await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
		await invalidateAll();
		window.location.href = '/auth/login';
	}
</script>

<svelte:window onclick={() => menuOpen = false} />

<nav class="bg-white shadow-sm border-b border-gray-200 hidden md:block">
	<div class="container mx-auto max-w-5xl px-4">
		<div class="flex items-center justify-between h-16">
			<a href="/" class="text-xl font-bold text-gray-900 flex items-center gap-2">
				<span>&#9917;</span> Table Soccer
			</a>

			<div class="flex items-center gap-1">
				{#each navLinks as link}
					<a
						href={link.href}
						class="px-3 py-2 rounded-lg text-sm font-medium transition-colors
							{currentPath === link.href
								? 'bg-gray-100 text-gray-900'
								: 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'}"
					>
						{link.label}
					</a>
				{/each}

				{#if user?.role === 'ADMIN'}
					<a
						href="/admin"
						class="px-3 py-2 rounded-lg text-sm font-medium transition-colors
							{currentPath === '/admin'
								? 'bg-gray-100 text-gray-900'
								: 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'}"
					>
						Admin
					</a>
				{/if}
			</div>

			<div class="flex items-center gap-3">
				{#if user}
					<!-- Profile dropdown -->
					<div class="relative">
						<button
							onclick={(e) => { e.stopPropagation(); menuOpen = !menuOpen; }}
							class="flex items-center gap-2 text-sm text-gray-700 hover:text-gray-900 cursor-pointer"
						>
							{#if user.avatarUrl}
								<img src={user.avatarUrl} alt={user.displayName} class="w-8 h-8 rounded-full" />
							{:else}
								<div class="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
									{user.displayName?.[0] ?? '?'}
								</div>
							{/if}
							<span class="hidden lg:inline">{user.displayName}</span>
							<svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
								<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
							</svg>
						</button>

						{#if menuOpen}
							<div class="absolute right-0 mt-2 w-48 bg-white rounded-xl shadow-lg border border-gray-100 py-1 z-50">
								<a href="/players/{user.id}" class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
									My Profile
								</a>
								<a href="/settings" class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
									Settings
								</a>
								<hr class="my-1 border-gray-100" />
								<button
									onclick={logout}
									class="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50"
								>
									Sign Out
								</button>
							</div>
						{/if}
					</div>
				{:else}
					<a href="/auth/login" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">
						Sign In
					</a>
				{/if}
			</div>
		</div>
	</div>
</nav>
