"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { ContentType } from "@/types/cms";
import Card, { CardHeader } from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";
import Modal, { FormField, TextInput, TextareaInput } from "@/components/ui/Modal";

export default function SchemaPage() {
  const [types, setTypes] = useState<ContentType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modal form states
  const [isOpen, setIsOpen] = useState(false);
  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [versionable, setVersionable] = useState(true);
  const [schedulable, setSchedulable] = useState(true);
  const [hasWorkflow, setHasWorkflow] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);
    try {
      await api.post("/api/v1/schema/content-types", {
        code,
        displayName,
        description,
        versionable,
        schedulable,
        hasWorkflow,
        fieldDefinitions: [],
      });
      setCode("");
      setDisplayName("");
      setDescription("");
      setVersionable(true);
      setSchedulable(true);
      setHasWorkflow(false);
      setIsOpen(false);
      await loadTypes();
    } catch (err: any) {
      setFormError(err.message || "Failed to create content type");
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => {
    loadTypes();
  }, []);

  const loadTypes = async () => {
    setLoading(true);
    try {
      const res = await api.get<ContentType[]>("/api/v1/schema/content-types");
      setTypes(Array.isArray(res) ? res : []);
    } catch (err: any) {
      setError(err.message || "Failed to load schema types");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Schema Builder</h1>
          <p className="text-sm text-slate-400 mt-1">
            Define and manage content types and their fields.
          </p>
        </div>
        <Button
          onClick={() => setIsOpen(true)}
          leftIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
          }
        >
          Create Type
        </Button>
      </div>

      <Card padding="none">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-surface-900 border-b border-surface-800 text-slate-400">
              <tr>
                <th className="px-6 py-4 font-semibold">Name / Code</th>
                <th className="px-6 py-4 font-semibold">Fields</th>
                <th className="px-6 py-4 font-semibold">Features</th>
                <th className="px-6 py-4 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-800">
              {loading ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center">
                    <Spinner />
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-red-400">
                    {error}
                  </td>
                </tr>
              ) : types.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center text-slate-500">
                    No content types found. Create one to get started.
                  </td>
                </tr>
              ) : (
                types.map((t) => (
                  <tr key={t.id} className="hover:bg-surface-800/50 transition-colors group">
                    <td className="px-6 py-4">
                      <p className="font-semibold text-slate-100">{t.displayName}</p>
                      <p className="text-xs text-slate-500 font-mono mt-0.5">{t.code}</p>
                    </td>
                    <td className="px-6 py-4">
                      <Badge variant="neutral">{t.fieldDefinitions?.length || 0} fields</Badge>
                    </td>
                    <td className="px-6 py-4 space-y-1.5">
                      <div className="flex gap-2">
                        {t.versionable && <Badge variant="info">Versioned</Badge>}
                        {t.schedulable && <Badge variant="success">Schedulable</Badge>}
                        {t.hasWorkflow && <Badge variant="brand">Workflow</Badge>}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <Button variant="ghost" size="sm">Edit</Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        open={isOpen}
        onClose={() => setIsOpen(false)}
        title="Create Content Type"
        description="Add a new content type definition to your schema."
      >
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div className="p-3 text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg">
              {formError}
            </div>
          )}

          <FormField label="Display Name" required>
            <TextInput
              required
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="e.g. Blog Post"
            />
          </FormField>

          <FormField label="Code" required>
            <TextInput
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="e.g. blog_post (lowercase, no spaces)"
              pattern="^[a-z0-9_]+$"
              title="Only lowercase letters, numbers, and underscores are allowed"
            />
          </FormField>

          <FormField label="Description">
            <TextareaInput
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the purpose of this content type..."
              rows={3}
            />
          </FormField>

          <div className="grid grid-cols-2 gap-4 pt-2">
            <label className="flex items-center gap-2.5 text-sm text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={versionable}
                onChange={(e) => setVersionable(e.target.checked)}
                className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-brand-500 focus:ring-brand-500"
              />
              <div>
                <p className="font-medium">Versionable</p>
                <p className="text-xs text-slate-500">Track history and versions</p>
              </div>
            </label>

            <label className="flex items-center gap-2.5 text-sm text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={schedulable}
                onChange={(e) => setSchedulable(e.target.checked)}
                className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-brand-500 focus:ring-brand-500"
              />
              <div>
                <p className="font-medium">Schedulable</p>
                <p className="text-xs text-slate-500">Enable publishing schedules</p>
              </div>
            </label>
          </div>

          <div className="pt-2">
            <label className="flex items-center gap-2.5 text-sm text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={hasWorkflow}
                onChange={(e) => setHasWorkflow(e.target.checked)}
                className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-brand-500 focus:ring-brand-500"
              />
              <div>
                <p className="font-medium">Has Workflow</p>
                <p className="text-xs text-slate-500">Require editorial approval steps</p>
              </div>
            </label>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-surface-800">
            <Button variant="ghost" type="button" onClick={() => setIsOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submitting}>
              Create Type
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
