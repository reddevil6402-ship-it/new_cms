"use client";

import React from "react";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  hover?: boolean;
  glass?: boolean;
  padding?: "none" | "sm" | "md" | "lg";
}

const paddingMap = {
  none: "",
  sm: "p-4",
  md: "p-5",
  lg: "p-6",
};

export function Card({
  children,
  className = "",
  hover = false,
  glass = false,
  padding = "md",
}: CardProps) {
  const base =
    "rounded-xl border shadow-card transition-all duration-200";
  const surface = glass
    ? "glass"
    : "bg-surface-900 border-surface-800";
  const hoverClass = hover
    ? "hover:border-surface-700 hover:shadow-card-hover hover:translate-y-[-1px]"
    : "";

  return (
    <div className={`${base} ${surface} ${hoverClass} ${paddingMap[padding]} ${className}`}>
      {children}
    </div>
  );
}

interface CardHeaderProps {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  icon?: React.ReactNode;
}

export function CardHeader({ title, subtitle, action, icon }: CardHeaderProps) {
  return (
    <div className="flex items-start justify-between mb-5">
      <div className="flex items-center gap-3">
        {icon && (
          <div className="flex items-center justify-center w-10 h-10 rounded-lg bg-brand-500/10 text-brand-400 shrink-0">
            {icon}
          </div>
        )}
        <div>
          <h3 className="text-sm font-semibold text-slate-100">{title}</h3>
          {subtitle && (
            <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>
          )}
        </div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

interface StatCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  change?: string;
  changeType?: "up" | "down" | "neutral";
  color?: "brand" | "green" | "amber" | "red";
  loading?: boolean;
}

const colorMap = {
  brand: {
    icon: "bg-brand-500/10 text-brand-400",
    value: "text-brand-400",
  },
  green: {
    icon: "bg-green-500/10 text-green-400",
    value: "text-green-400",
  },
  amber: {
    icon: "bg-amber-500/10 text-amber-400",
    value: "text-amber-400",
  },
  red: {
    icon: "bg-red-500/10 text-red-400",
    value: "text-red-400",
  },
};

export function StatCard({
  label,
  value,
  icon,
  change,
  changeType = "neutral",
  color = "brand",
  loading = false,
}: StatCardProps) {
  const colors = colorMap[color];

  return (
    <Card hover className="animate-fade-in">
      <div className="flex items-center justify-between">
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">
            {label}
          </p>
          {loading ? (
            <div className="skeleton h-8 w-24 mb-1" />
          ) : (
            <p className={`text-2xl font-bold ${colors.value}`}>{value}</p>
          )}
          {change && !loading && (
            <p
              className={`text-xs mt-1.5 ${
                changeType === "up"
                  ? "text-green-400"
                  : changeType === "down"
                  ? "text-red-400"
                  : "text-slate-500"
              }`}
            >
              {change}
            </p>
          )}
        </div>
        <div
          className={`flex items-center justify-center w-12 h-12 rounded-xl shrink-0 ${colors.icon}`}
        >
          {icon}
        </div>
      </div>
    </Card>
  );
}

export default Card;
