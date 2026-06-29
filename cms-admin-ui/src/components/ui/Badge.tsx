"use client";

import React from "react";

type BadgeVariant =
  | "brand"
  | "success"
  | "warning"
  | "danger"
  | "neutral"
  | "info";

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  dot?: boolean;
  className?: string;
}

const variantMap: Record<BadgeVariant, string> = {
  brand: "bg-brand-500/15 text-brand-300 border-brand-500/25",
  success: "bg-green-500/15 text-green-300 border-green-500/25",
  warning: "bg-amber-500/15 text-amber-300 border-amber-500/25",
  danger: "bg-red-500/15 text-red-300 border-red-500/25",
  neutral: "bg-slate-700/50 text-slate-400 border-slate-700",
  info: "bg-sky-500/15 text-sky-300 border-sky-500/25",
};

const dotMap: Record<BadgeVariant, string> = {
  brand: "bg-brand-400",
  success: "bg-green-400",
  warning: "bg-amber-400",
  danger: "bg-red-400",
  neutral: "bg-slate-400",
  info: "bg-sky-400",
};

export function Badge({
  children,
  variant = "neutral",
  dot = false,
  className = "",
}: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-xs font-medium border ${variantMap[variant]} ${className}`}
    >
      {dot && (
        <span
          className={`w-1.5 h-1.5 rounded-full shrink-0 ${dotMap[variant]}`}
        />
      )}
      {children}
    </span>
  );
}

// Status-to-variant helpers for common CMS statuses
export function ContentStatusBadge({ status }: { status: string }) {
  const map: Record<string, BadgeVariant> = {
    DRAFT: "neutral",
    REVIEW: "warning",
    PUBLISHED: "success",
    ARCHIVED: "danger",
    ACTIVE: "success",
    INACTIVE: "danger",
    SUSPENDED: "warning",
    RECEIVED: "info",
    PROCESSING: "warning",
    PROCESSED: "success",
    REJECTED: "danger",
  };
  return (
    <Badge variant={map[status] ?? "neutral"} dot>
      {status}
    </Badge>
  );
}

export default Badge;
