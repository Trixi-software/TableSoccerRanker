import type { User } from '$lib/api/types';
import { env } from '$env/dynamic/private';

export async function load({ request, fetch: svelteKitFetch }) {
	const internalApiUrl = env.INTERNAL_API_URL;

	async function fetchJson<T>(path: string): Promise<T | null> {
		try {
			const res = internalApiUrl
				? await fetch(`${internalApiUrl}${path}`, {
						headers: { cookie: request.headers.get('cookie') || '' }
					})
				: await svelteKitFetch(path);
			if (!res.ok) return null;
			return await res.json();
		} catch {
			return null;
		}
	}

	const [user, authConfig] = await Promise.all([
		fetchJson<User>('/api/auth/me'),
		fetchJson<{ passwordAuthEnabled: boolean }>('/api/auth/config')
	]);

	return {
		user: user as User | null,
		passwordAuthEnabled: authConfig?.passwordAuthEnabled ?? false
	};
}
