"use client";

import React, { useEffect, useRef } from "react";
import Button from "./Button";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
}

const sizeMap = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
  xl: "max-w-2xl",
};

export default function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = "md",
}: ModalProps) {
  const overlayRef = useRef<HTMLDivElement>(null);

  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [open, onClose]);

  // Prevent body scroll when modal open
  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => { document.body.style.overflow = ""; };
  }, [open]);

  if (!open) return null;

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
      onClick={(e) => { if (e.target === overlayRef.current) onClose(); }}
    >
      <div
        className={`relative w-full ${sizeMap[size]} bg-surface-900 border border-surface-700 rounded-2xl shadow-2xl animate-slide-up`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        {/* Header */}
        <div className="flex items-start justify-between p-5 border-b border-surface-800">
          <div>
            <h2
              id="modal-title"
              className="text-base font-semibold text-slate-100"
            >
              {title}
            </h2>
            {description && (
              <p className="text-sm text-slate-500 mt-0.5">{description}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-200 hover:bg-surface-800 rounded-lg p-1.5 transition-colors ml-4 shrink-0"
            aria-label="Close modal"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Body */}
        <div className="p-5">{children}</div>

        {/* Footer */}
        {footer && (
          <div className="flex items-center justify-end gap-3 px-5 pb-5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Form Input helper used in modals ─────────────────────────────────────────
interface FormFieldProps {
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}

export function FormField({ label, required, error, children }: FormFieldProps) {
  return (
    <div className="mb-4">
      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
        {label}
        {required && <span className="text-red-400 ml-1">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
    </div>
  );
}

interface TextInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
}

export function TextInput({ error, className = "", ...props }: TextInputProps) {
  return (
    <input
      className={`w-full px-3 py-2 text-sm bg-surface-800 border rounded-lg text-slate-100 placeholder-slate-500 
        focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent
        transition-colors
        ${error ? "border-red-500/50 focus:ring-red-500" : "border-surface-700"}
        ${className}`}
      {...props}
    />
  );
}

interface TextareaInputProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean;
}

export function TextareaInput({ error, className = "", ...props }: TextareaInputProps) {
  return (
    <textarea
      className={`w-full px-3 py-2 text-sm bg-surface-800 border rounded-lg text-slate-100 placeholder-slate-500
        focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent
        transition-colors resize-y
        ${error ? "border-red-500/50 focus:ring-red-500" : "border-surface-700"}
        ${className}`}
      {...props}
    />
  );
}

interface SelectInputProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  children: React.ReactNode;
}

export function SelectInput({ children, className = "", ...props }: SelectInputProps) {
  return (
    <select
      className={`w-full px-3 py-2 text-sm bg-surface-800 border border-surface-700 rounded-lg text-slate-100
        focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent
        transition-colors appearance-none
        ${className}`}
      {...props}
    >
      {children}
    </select>
  );
}
