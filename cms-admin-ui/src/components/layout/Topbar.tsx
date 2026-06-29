"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";

// ─── Breadcrumb from pathname ─────────────────────────────────────────────────
function useBreadcrumbs() {
  const pathname = usePathname();
  const parts = pathname
    .split("/")
    .filter(Boolean)
    .map((part, index, arr) => ({
      label: part.charAt(0).toUpperCase() + part.slice(1).replace(/-/g, " "),
      href: "/" + arr.slice(0, index + 1).join("/"),
      isLast: index === arr.length - 1,
    }));
  return parts;
}

// ─── Topbar ───────────────────────────────────────────────────────────────────
export default function Topbar() {
  const { user } = useAuth();
  const breadcrumbs = useBreadcrumbs();

  return (
    <header className="fixed top-0 right-0 left-0 h-16 bg-surface-950/80 backdrop-blur-md border-b border-surface-800 flex items-center justify-between px-6 z-20">
      {/* Left: breadcrumbs — offset for sidebar */}
      <nav className="flex items-center gap-1.5 text-sm ml-64 transition-all duration-300" aria-label="Breadcrumb">
        {breadcrumbs.map((crumb, i) => (
          <React.Fragment key={crumb.href}>
            {i > 0 && (
              <svg className="w-3.5 h-3.5 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            )}
            <span
              className={`font-medium ${
                crumb.isLast
                  ? "text-slate-200"
                  : "text-slate-500 hover:text-slate-300"
              }`}
            >
              {crumb.label}
            </span>
          </React.Fragment>
        ))}
      </nav>

      {/* Right: actions */}
      <div className="flex items-center gap-3">
        {/* Tenant badge */}
        <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-surface-800 border border-surface-700 rounded-lg">
          <div className="w-1.5 h-1.5 rounded-full bg-green-400" />
          <span className="text-xs font-medium text-slate-400">
            {user?.tcode ?? "—"}
          </span>
        </div>

        {/* Notification bell */}
        <button className="relative w-9 h-9 flex items-center justify-center rounded-lg text-slate-500 hover:text-slate-200 hover:bg-surface-800 transition-colors">
          <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
        </button>

        {/* User avatar */}
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-full bg-brand-gradient flex items-center justify-center shadow-brand-glow-sm">
            <span className="text-xs font-bold text-white">
              {user?.email?.[0]?.toUpperCase() ?? "U"}
            </span>
          </div>
          <div className="hidden md:block">
            <p className="text-xs font-semibold text-slate-300 leading-tight truncate max-w-[140px]">
              {user?.email ?? "—"}
            </p>
            <p className="text-[10px] text-slate-500">Administrator</p>
          </div>
        </div>
      </div>
    </header>
  );
}
