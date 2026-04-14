<script lang="ts">
	import { page } from '$app/stores';
	import { goto, invalidateAll } from '$app/navigation';
	import { api } from '$lib/api/client';

	let error = $derived($page.url.searchParams.get('error'));
	let passwordAuthEnabled = $derived($page.data.passwordAuthEnabled);
	let mode: 'login' | 'register' = $state('login');
	let username = $state('');
	let password = $state('');
	let displayName = $state('');
	let formError: string | null = $state(null);
	let loading = $state(false);

	async function handleLogin() {
		formError = null;
		loading = true;
		try {
			await api.post('/api/auth/login', { username, password });
			await invalidateAll();
			goto('/');
		} catch (e) {
			formError = 'Invalid username or password.';
		} finally {
			loading = false;
		}
	}

	async function handleRegister() {
		formError = null;
		loading = true;
		try {
			await api.post('/api/auth/register', { username, password, displayName });
			await api.post('/api/auth/login', { username, password });
			await invalidateAll();
			goto('/');
		} catch (e) {
			const msg = (e as Error).message || '';
			if (msg.includes('already exists')) {
				formError = 'Username is already taken. Please choose a different one.';
			} else {
				formError = 'Registration failed. Please try a different username.';
			}
		} finally {
			loading = false;
		}
	}
</script>

<div class="flex items-center justify-center min-h-[60vh]">
	<div class="bg-white rounded-2xl shadow-lg border border-brand-cloud-blue p-8 max-w-sm w-full animate-fade-in-up">
		<div class="text-center mb-6">
			<!-- Brand mark -->
			<div class="flex items-center justify-center gap-1 mb-3">
				<span class="text-brand-yellow text-5xl font-black">{"{"}</span>
				<span class="text-brand-blue text-4xl font-black">TS</span>
				<span class="text-brand-yellow text-5xl font-black">{"}"}</span>
			</div>
			<h1 class="text-2xl font-black text-gray-900 mb-1">Table Soccer Ranker</h1>
			<p class="text-brand-blue/60 text-sm font-semibold">by Trixi Software</p>
		</div>

		{#if error}
			<div class="bg-red-50 text-red-700 rounded-lg p-3 mb-4 text-sm border border-red-100">
				Login failed. Only @trixi.cz accounts are allowed.
			</div>
		{/if}

		{#if formError}
			<div class="bg-red-50 text-red-700 rounded-lg p-3 mb-4 text-sm border border-red-100">
				{formError}
			</div>
		{/if}

		{#if passwordAuthEnabled}
			<!-- Username/Password form -->
			<form onsubmit={(e) => { e.preventDefault(); mode === 'login' ? handleLogin() : handleRegister(); }}>
				<div class="space-y-3">
					<input
						type="text"
						bind:value={username}
						placeholder="Username"
						required
						class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
					/>
					{#if mode === 'register'}
						<input
							type="text"
							bind:value={displayName}
							placeholder="Display Name"
							required
							class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
						/>
					{/if}
					<input
						type="password"
						bind:value={password}
						placeholder="Password"
						required
						minlength={mode === 'register' ? 6 : undefined}
						class="w-full px-4 py-3 border border-brand-gray rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent transition-shadow"
					/>
					<button
						type="submit"
						disabled={loading}
						class="w-full py-3 bg-brand-blue text-white rounded-xl font-bold hover:bg-brand-blue/90 disabled:bg-brand-gray transition-colors"
					>
						{loading ? '...' : mode === 'login' ? 'Sign In' : 'Create Account'}
					</button>
				</div>
			</form>

			<button
				onclick={() => { mode = mode === 'login' ? 'register' : 'login'; formError = null; }}
				class="w-full text-center text-sm text-brand-blue hover:text-brand-it-blue font-semibold mt-3 transition-colors"
			>
				{mode === 'login' ? "Don't have an account? Register" : 'Already have an account? Sign In'}
			</button>

			<!-- Divider -->
			<div class="flex items-center gap-3 my-5">
				<div class="flex-1 h-px bg-brand-gray"></div>
				<span class="text-xs text-gray-400 font-semibold">OR</span>
				<div class="flex-1 h-px bg-brand-gray"></div>
			</div>
		{/if}

		<!-- Google OAuth -->
		<a
			href="/oauth2/authorization/google"
			class="inline-flex items-center gap-3 bg-white border-2 border-brand-gray rounded-xl px-6 py-3 text-gray-700 font-semibold hover:bg-brand-cloud-blue hover:border-brand-it-blue transition-all w-full justify-center"
		>
			<svg class="w-5 h-5" viewBox="0 0 24 24">
				<path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
				<path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
				<path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
				<path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
			</svg>
			Sign in with Google
		</a>
	</div>
</div>
