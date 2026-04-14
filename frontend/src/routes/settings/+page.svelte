<script lang="ts">
	import { page } from '$app/stores';
	import { api } from '$lib/api/client';

	let user = $derived($page.data.user);
	let passwordAuthEnabled = $derived($page.data.passwordAuthEnabled);

	let currentPassword = $state('');
	let newPassword = $state('');
	let confirmPassword = $state('');
	let passwordError: string | null = $state(null);
	let passwordSuccess = $state(false);
	let saving = $state(false);

	async function changePassword() {
		passwordError = null;
		passwordSuccess = false;

		if (newPassword.length < 6) {
			passwordError = 'New password must be at least 6 characters';
			return;
		}
		if (newPassword !== confirmPassword) {
			passwordError = 'Passwords do not match';
			return;
		}

		saving = true;
		try {
			await api.put('/api/auth/password', { currentPassword, newPassword });
			passwordSuccess = true;
			currentPassword = '';
			newPassword = '';
			confirmPassword = '';
		} catch (e) {
			passwordError = (e as Error).message || 'Failed to change password';
		} finally {
			saving = false;
		}
	}
</script>

{#if !user}
	<div class="text-center py-12 text-gray-400">Please sign in</div>
{:else}
	<div class="space-y-6 max-w-lg mx-auto">
		<h1 class="text-2xl font-black text-gray-900 brand-heading">Settings</h1>

		<!-- Profile info -->
		<div class="bg-white rounded-2xl border border-brand-cloud-blue p-6 animate-fade-in-up">
			<h2 class="font-bold text-gray-900 mb-4">Profile</h2>
			<div class="flex items-center gap-4">
				{#if user.avatarUrl}
					<img src={user.avatarUrl} alt={user.displayName} class="w-16 h-16 rounded-full" />
				{:else}
					<div class="w-16 h-16 rounded-full bg-brand-cloud-blue text-brand-blue flex items-center justify-center text-2xl font-bold">
						{user.displayName?.[0] ?? '?'}
					</div>
				{/if}
				<div>
					<p class="text-lg font-semibold text-gray-900">{user.displayName}</p>
					<p class="text-sm text-gray-500">{user.email}</p>
					<span class="inline-block mt-1 px-2 py-0.5 rounded-full text-xs font-bold
						{user.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-brand-cloud-blue text-brand-blue'}">
						{user.role}
					</span>
				</div>
			</div>
		</div>

		{#if passwordAuthEnabled}
		<!-- Change password -->
		<div class="bg-white rounded-2xl border border-brand-cloud-blue p-6 animate-fade-in-up" style="--delay: 80ms">
			<h2 class="font-bold text-gray-900 mb-4">Change Password</h2>

			{#if passwordSuccess}
				<div class="bg-green-50 text-green-700 rounded-lg p-3 mb-4 text-sm border border-green-100">
					Password changed successfully.
				</div>
			{/if}

			{#if passwordError}
				<div class="bg-red-50 text-red-700 rounded-lg p-3 mb-4 text-sm border border-red-100">
					{passwordError}
				</div>
			{/if}

			<form onsubmit={(e) => { e.preventDefault(); changePassword(); }}>
				<div class="space-y-3">
					<div>
						<label for="current" class="block text-sm font-semibold text-gray-700 mb-1">Current Password</label>
						<input
							id="current"
							type="password"
							bind:value={currentPassword}
							required
							class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
						/>
					</div>
					<div>
						<label for="new" class="block text-sm font-semibold text-gray-700 mb-1">New Password</label>
						<input
							id="new"
							type="password"
							bind:value={newPassword}
							required
							minlength="6"
							class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
						/>
					</div>
					<div>
						<label for="confirm" class="block text-sm font-semibold text-gray-700 mb-1">Confirm New Password</label>
						<input
							id="confirm"
							type="password"
							bind:value={confirmPassword}
							required
							minlength="6"
							class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
						/>
					</div>
					<button
						type="submit"
						disabled={saving}
						class="w-full py-3 bg-brand-blue text-white rounded-xl font-bold hover:bg-brand-blue/90 disabled:bg-brand-gray transition-colors"
					>
						{saving ? 'Saving...' : 'Change Password'}
					</button>
				</div>
			</form>
		</div>
		{/if}
	</div>
{/if}
