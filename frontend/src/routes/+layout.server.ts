import type { User } from '$lib/api/types';
import { env } from '$env/dynamic/private';

export async function load({ request, fetch: svelteKitFetch }) {
	try {
		const internalApiUrl = env.INTERNAL_API_URL;
		const res = internalApiUrl
			? await fetch(`${internalApiUrl}/api/auth/me`, {
					headers: { cookie: request.headers.get('cookie') || '' }
				})
			: await svelteKitFetch('/api/auth/me');

		if (!res.ok) return { user: null as User | null };
		const user: User = await res.json();
		return { user: user as User | null };
	} catch {
		return { user: null as User | null };
	}
}
