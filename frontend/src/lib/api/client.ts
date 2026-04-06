const BASE = '';

function getCsrfToken(): string | null {
	const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
	return match ? decodeURIComponent(match[1]) : null;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
	const headers: Record<string, string> = {
		'Content-Type': 'application/json',
		...(options?.headers as Record<string, string>)
	};

	// Add CSRF token for state-changing requests
	const method = options?.method?.toUpperCase() || 'GET';
	if (method !== 'GET' && method !== 'HEAD') {
		const token = getCsrfToken();
		if (token) headers['X-XSRF-TOKEN'] = token;
	}

	const res = await fetch(`${BASE}${path}`, {
		credentials: 'include',
		headers,
		...options
	});
	if (res.status === 401) {
		window.location.href = '/auth/login';
		throw new Error('Unauthorized');
	}
	if (!res.ok) {
		const body = await res.json().catch(() => ({}));
		throw new Error(body.detail || `Request failed: ${res.status}`);
	}
	if (res.status === 204) return undefined as T;
	return res.json();
}

export const api = {
	get: <T>(path: string) => request<T>(path),
	post: <T>(path: string, body?: unknown) =>
		request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
	put: <T>(path: string, body?: unknown) =>
		request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
	delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
	upload: <T>(path: string, formData: FormData) => {
		const csrfToken = getCsrfToken();
		const headers: Record<string, string> = {};
		if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;
		return request<T>(path, {
			method: 'POST',
			body: formData,
			headers
		});
	}
};
