"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";

// ─── Nav item definition ──────────────────────────────────────────────────────
interface NavItem {
  label: string;
  href: string;
  icon: React.ReactNode;
  badge?: string;
}

function LayoutIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>;
}
function SchemaIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 10h16M4 14h8M4 18h8"/><circle cx="18" cy="16" r="3"/><path strokeLinecap="round" d="M18 13v-2M18 19v2M15.27 14.27l-1.42-1.42M20.15 17.15l1.42 1.42M13 16h2M21 16h2M15.27 17.73l-1.42 1.42M20.15 14.85l1.42-1.42" style={{strokeWidth: 0}}/></svg>;
}
function ContentIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>;
}
function MediaIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>;
}
function FormIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"/></svg>;
}
function SettingsIcon() {
  return <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>;
}

const navItems: NavItem[] = [
  { label: "Dashboard", href: "/dashboard", icon: <LayoutIcon /> },
  { label: "Schema Builder", href: "/dashboard/schema", icon: <SchemaIcon /> },
  { label: "Content", href: "/dashboard/content", icon: <ContentIcon /> },
  { label: "Media Library", href: "/dashboard/media", icon: <MediaIcon /> },
  { label: "Forms", href: "/dashboard/forms", icon: <FormIcon /> },
  { label: "Settings", href: "/dashboard/settings", icon: <SettingsIcon /> },
];

// ─── Sidebar ──────────────────────────────────────────────────────────────────
export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const isActive = (href: string) => {
    if (href === "/dashboard") return pathname === "/dashboard";
    return pathname.startsWith(href);
  };

  return (
    <aside
      className={`fixed left-0 top-0 h-screen bg-surface-900 border-r border-surface-800 flex flex-col z-30 transition-all duration-300 ${
        collapsed ? "w-16" : "w-64"
      }`}
    >
      {/* Brand header */}
      <div className="flex items-center h-16 px-4 border-b border-surface-800 shrink-0">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-brand-gradient shrink-0 shadow-brand-glow-sm">
          <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
        </div>
        {!collapsed && (
          <div className="ml-3 min-w-0">
            <p className="text-sm font-bold text-slate-100 truncate">NextGen CMS</p>
            <p className="text-[10px] text-slate-500 truncate">Enterprise Platform</p>
          </div>
        )}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="ml-auto text-slate-500 hover:text-slate-300 hover:bg-surface-800 p-1.5 rounded-lg transition-colors shrink-0"
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          <svg className={`w-4 h-4 transition-transform duration-300 ${collapsed ? "rotate-180" : ""}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
          </svg>
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4 px-2 space-y-0.5 overflow-y-auto overflow-x-hidden">
        {navItems.map((item) => {
          const active = isActive(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              title={collapsed ? item.label : undefined}
              className={`group flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 relative
                ${
                  active
                    ? "bg-brand-500/15 text-brand-300 shadow-brand-glow-sm"
                    : "text-slate-400 hover:text-slate-100 hover:bg-surface-800"
                }
                ${collapsed ? "justify-center" : ""}
              `}
            >
              {active && (
                <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-brand-500 rounded-r-full" />
              )}
              <span className={`shrink-0 ${active ? "text-brand-400" : "text-slate-500 group-hover:text-slate-300"}`}>
                {item.icon}
              </span>
              {!collapsed && (
                <span className="truncate">{item.label}</span>
              )}
              {!collapsed && item.badge && (
                <span className="ml-auto shrink-0 px-1.5 py-0.5 text-[10px] font-semibold bg-brand-500/20 text-brand-400 rounded-md">
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}
      </nav>

      {/* User profile */}
      <div className="shrink-0 border-t border-surface-800 p-3">
        <div className={`flex items-center gap-3 rounded-lg p-2 hover:bg-surface-800 transition-colors ${collapsed ? "justify-center" : ""}`}>
          <div className="w-8 h-8 rounded-full bg-brand-gradient flex items-center justify-center shrink-0 shadow-brand-glow-sm">
            <span className="text-xs font-bold text-white">
              {user?.email?.[0]?.toUpperCase() ?? "U"}
            </span>
          </div>
          {!collapsed && (
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-slate-300 truncate">
                {user?.email ?? "Unknown"}
              </p>
              <p className="text-[10px] text-slate-500 truncate capitalize">
                {user?.permissions?.[0]?.replace("ROLE_", "").toLowerCase() ?? "user"}
              </p>
            </div>
          )}
          {!collapsed && (
            <button
              onClick={() => logout()}
              className="shrink-0 text-slate-500 hover:text-red-400 p-1 rounded-md transition-colors"
              title="Logout"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </button>
          )}
        </div>
      </div>
    </aside>
  );
}
