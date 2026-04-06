<script lang="ts">
	import { page } from '$app/stores';

	let currentPath = $derived($page.url.pathname);

	const navItems = [
		{ href: '/', label: 'Rankings', icon: '\u{1F3C6}' },
		{ href: '/next-match', label: 'Play', icon: '\u26BD' },
		{ href: '/matches', label: 'Matches', icon: '\u{1F4CB}' },
		{ href: '/stats', label: 'Stats', icon: '\u{1F4C8}' }
	];
</script>

<nav class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 md:hidden z-50">
	<div class="flex justify-around items-center h-16">
		{#each navItems as item}
			<a
				href={item.href}
				class="flex flex-col items-center justify-center gap-0.5 min-w-[64px] py-1 transition-colors
					{currentPath === item.href ? 'text-blue-600' : 'text-gray-500'}"
			>
				<span class="text-xl">{item.icon}</span>
				<span class="text-[10px] font-medium">{item.label}</span>
			</a>
		{/each}
	</div>
</nav>
