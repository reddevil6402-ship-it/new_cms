"use client";

import React from "react";

interface SpinnerProps {
  size?: "sm" | "md" | "lg";
  className?: string;
}

const sizeMap = {
  sm: "w-3.5 h-3.5 border-2",
  md: "w-5 h-5 border-2",
  lg: "w-8 h-8 border-[3px]",
};

export default function Spinner({ size = "md", className = "" }: SpinnerProps) {
  return (
    <span
      className={`inline-block rounded-full border-slate-600 border-t-brand-500 animate-spin ${sizeMap[size]} ${className}`}
      role="status"
      aria-label="Loading"
    />
  );
}

export function FullPageSpinner() {
  return (
    <div className="fixed inset-0 bg-surface-950 flex items-center justify-center z-50">
      <div className="flex flex-col items-center gap-4">
        <div className="relative">
          <div className="w-16 h-16 rounded-full border-[3px] border-surface-800" />
          <div className="absolute inset-0 w-16 h-16 rounded-full border-[3px] border-transparent border-t-brand-500 animate-spin" />
        </div>
        <p className="text-sm text-slate-500 font-medium">Loading…</p>
      </div>
    </div>
  );
}
