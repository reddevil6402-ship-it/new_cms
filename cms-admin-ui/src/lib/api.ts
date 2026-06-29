import { getAccessToken, getTenantId, refresh } from "./auth";

const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// ─── Core fetch wrapper ───────────────────────────────────────────────────────
async function request<T>(
  url: string,
  options: RequestInit = {},
  overrideTenantId?: string
): Promise<T> {
  const token = getAccessToken();
  const tenantId = overrideTenantId ?? getTenantId();

  const buildHeaders = (tok: string | null): Record<string, string> => ({
    "Content-Type": "application/json",
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
    ...(tenantId ? { "X-Tenant-Id": tenantId } : {}),
    ...(options.headers as Record<string, string>),
  });

  let res = await fetch(`${BASE}${url}`, {
    ...options,
    headers: buildHeaders(token),
    credentials: "include",
  });

  // Transparent token refresh on 401
  if (res.status === 401) {
    const refreshed = await refresh();
    if (refreshed) {
      res = await fetch(`${BASE}${url}`, {
        ...options,
        headers: buildHeaders(getAccessToken()),
        credentials: "include",
      });
    }
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: { message: "Request failed" } }));
    throw new Error(err?.error?.message || `HTTP ${res.status}`);
  }

  // 204 No Content
  if (res.status === 204) return undefined as T;

  return res.json() as Promise<T>;
}

// ─── Multipart upload wrapper ─────────────────────────────────────────────────
export async function uploadFile<T>(url: string, formData: FormData): Promise<T> {
  const token = getAccessToken();
  const tenantId = getTenantId();

  const headers: Record<string, string> = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(tenantId ? { "X-Tenant-Id": tenantId } : {}),
  };

  const res = await fetch(`${BASE}${url}`, {
    method: "POST",
    headers,
    body: formData,
    credentials: "include",
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: { message: "Upload failed" } }));
    throw new Error(err?.error?.message || `HTTP ${res.status}`);
  }

  return res.json() as Promise<T>;
}

// ─── Typed HTTP verbs ─────────────────────────────────────────────────────────
export const api = {
  get: <T>(url: string, tenantId?: string) =>
    request<T>(url, { method: "GET" }, tenantId),

  post: <T>(url: string, body: unknown, tenantId?: string) =>
    request<T>(url, { method: "POST", body: JSON.stringify(body) }, tenantId),

  put: <T>(url: string, body: unknown, tenantId?: string) =>
    request<T>(url, { method: "PUT", body: JSON.stringify(body) }, tenantId),

  patch: <T>(url: string, body: unknown, tenantId?: string) =>
    request<T>(url, { method: "PATCH", body: JSON.stringify(body) }, tenantId),

  delete: <T>(url: string, tenantId?: string) =>
    request<T>(url, { method: "DELETE" }, tenantId),
};
