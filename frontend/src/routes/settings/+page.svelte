<script lang="ts">
	import { page } from '$app/stores';
	import { api } from '$lib/api/client';

	let user = $derived($page.data.user);

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
		<h1 class="text-2xl font-bold text-gray-900">Settings</h1>

		<!-- Profile info -->
		<div class="bg-white rounded-xl shadow-sm p-6">
			<h2 class="font-semibold text-gray-900 mb-4">Profile</h2>
			<div class="flex items-center gap-4">
				{#if user.avatarUrl}
					<img src={user.avatarUrl} alt={user.displayName} class="w-16 h-16 rounded-full" />
				{:else}
					<div class="w-16 h-16 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-2xl font-bold">
						{user.displayName?.[0] ?? '?'}
					</div>
				{/if}
				<div>
					<p class="text-lg font-medium text-gray-900">{user.displayName}</p>
					<p class="text-sm text-gray-500">{user.email}</p>
					<span class="inline-block mt-1 px-2 py-0.5 rounded-full text-xs font-medium
						{user.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-600'}">
						{user.role}
					</span>
				</div>
			</div>
		</div>

		<!-- Change password -->
		<div class="bg-white rounded-xl shadow-sm p-6">
			<h2 class="font-semibold text-gray-900 mb-4">Change Password</h2>

			{#if passwordSuccess}
				<div class="bg-green-50 text-green-700 rounded-lg p-3 mb-4 text-sm">
					Password changed successfully.
				</div>
			{/if}

			{#if passwordError}
				<div class="bg-red-50 text-red-700 rounded-lg p-3 mb-4 text-sm">
					{passwordError}
				</div>
			{/if}

			<form onsubmit={(e) => { e.preventDefault(); changePassword(); }}>
				<div class="space-y-3">
					<div>
						<label for="current" class="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
						<input
							id="current"
							type="password"
							bind:value={currentPassword}
							required
							class="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						/>
					</div>
					<div>
						<label for="new" class="block text-sm font-medium text-gray-700 mb-1">New Password</label>
						<input
							id="new"
							type="password"
							bind:value={newPassword}
							required
							minlength="6"
							class="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						/>
					</div>
					<div>
						<label for="confirm" class="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
						<input
							id="confirm"
							type="password"
							bind:value={confirmPassword}
							required
							minlength="6"
							class="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
						/>
					</div>
					<button
						type="submit"
						disabled={saving}
						class="w-full py-3 bg-blue-600 text-white rounded-xl font-medium hover:bg-blue-700 disabled:bg-gray-300 transition-colors"
					>
						{saving ? 'Saving...' : 'Change Password'}
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}
