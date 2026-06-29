"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import type { JwtClaims } from "@/types/cms";
import {
  login as authLogin,
  logout as authLogout,
  refresh,
  getClaims,
  isAuthenticated,
} from "@/lib/auth";

// ─── Context shape ────────────────────────────────────────────────────────────
interface AuthContextValue {
  user: JwtClaims | null;
  isLoading: boolean;
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// ─── Provider ─────────────────────────────────────────────────────────────────
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<JwtClaims | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // On mount: attempt to restore session using the HttpOnly refresh cookie.
  // If no valid cookie, the refresh endpoint returns 401 and we stay logged out.
  useEffect(() => {
    const restoreSession = async () => {
      if (isAuthenticated()) {
        setUser(getClaims());
        setIsLoading(false);
        return;
      }

      const ok = await refresh();
      if (ok) {
        setUser(getClaims());
      }
      setIsLoading(false);
    };

    restoreSession();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const claims = await authLogin(email, password);
    setUser(claims);
    router.push("/dashboard");
  }, [router]);

  const logout = useCallback(async () => {
    await authLogout();
    setUser(null);
    router.push("/login");
  }, [router]);

  return (
    <AuthContext.Provider
      value={{ user, isLoading, isLoggedIn: !!user, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// ─── Hook ─────────────────────────────────────────────────────────────────────
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
