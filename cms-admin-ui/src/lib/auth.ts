import type { JwtClaims, LoginResponse } from "@/types/cms";

// ─── In-memory token store ────────────────────────────────────────────────────
// Access token lives ONLY in JS memory — never localStorage, never sessionStorage.
// Refresh token is an HttpOnly Secure cookie managed entirely by the browser.

let _accessToken: string | null = null;
let _claims: JwtClaims | null = null;

// ─── Token accessors ──────────────────────────────────────────────────────────
export function getAccessToken(): string | null {
  return _accessToken;
}

export function getClaims(): JwtClaims | null {
  return _claims;
}

export function getTenantId(): string | null {
  return _claims?.tid ?? null;
}

export function getUserId(): string | null {
  return _claims?.sub ?? null;
}

export function isAuthenticated(): boolean {
  if (!_accessToken || !_claims) return false;
  return _claims.exp * 1000 > Date.now();
}

// ─── JWT parser (client-side, no signature verification — that's the gateway's job) ─
function parseJwt(token: string): JwtClaims | null {
  try {
    const base64Payload = token.split(".")[1];
    const decoded = atob(base64Payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decoded) as JwtClaims;
  } catch {
    return null;
  }
}

function applyToken(token: string): void {
  _accessToken = token;
  _claims = parseJwt(token);
}

function clearToken(): void {
  _accessToken = null;
  _claims = null;
}

// ─── Auth actions ─────────────────────────────────────────────────────────────
const BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function login(email: string, password: string): Promise<JwtClaims> {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.error?.message || "Invalid credentials. Please try again.");
  }

  const body = await res.json();
  const data: LoginResponse = body.data;
  applyToken(data.accessToken);
  return _claims!;
}

export async function refresh(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/api/v1/auth/refresh`, {
      method: "POST",
      credentials: "include",
    });

    if (!res.ok) {
      clearToken();
      return false;
    }

    const body = await res.json();
    applyToken(body.data.accessToken);
    return true;
  } catch {
    clearToken();
    return false;
  }
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${BASE}/api/v1/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers: {
        Authorization: `Bearer ${_accessToken}`,
      },
    });
  } finally {
    clearToken();
  }
}
