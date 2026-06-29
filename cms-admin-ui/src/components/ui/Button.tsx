"use client";

import React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import Spinner from "./Spinner";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 font-medium rounded-lg transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950 disabled:opacity-50 disabled:pointer-events-none select-none",
  {
    variants: {
      variant: {
        primary:
          "bg-brand-gradient text-white shadow-brand-glow-sm hover:shadow-brand-glow hover:scale-[1.02] active:scale-[0.98]",
        secondary:
          "bg-surface-800 text-slate-200 border border-surface-700 hover:bg-surface-700 hover:border-surface-600 active:bg-surface-800",
        ghost:
          "text-slate-400 hover:text-slate-100 hover:bg-surface-800 active:bg-surface-700",
        danger:
          "bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 hover:border-red-500/40 active:bg-red-500/10",
        outline:
          "border border-brand-500/40 text-brand-400 hover:bg-brand-500/10 hover:border-brand-500/60 active:bg-brand-500/5",
      },
      size: {
        sm: "px-3 py-1.5 text-xs h-7",
        md: "px-4 py-2 text-sm h-9",
        lg: "px-5 py-2.5 text-sm h-10",
        xl: "px-6 py-3 text-base h-12",
        icon: "w-9 h-9 p-0",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  }
);

interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export default function Button({
  className,
  variant,
  size,
  loading,
  leftIcon,
  rightIcon,
  children,
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      className={buttonVariants({ variant, size, className })}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? (
        <Spinner size="sm" />
      ) : (
        leftIcon && <span className="shrink-0">{leftIcon}</span>
      )}
      {children}
      {rightIcon && !loading && (
        <span className="shrink-0">{rightIcon}</span>
      )}
    </button>
  );
}

export { buttonVariants };
