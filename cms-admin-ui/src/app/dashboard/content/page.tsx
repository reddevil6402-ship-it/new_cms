"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { ContentItem, ContentType } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import { ContentStatusBadge } from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";
import Modal, { FormField, TextInput, SelectInput } from "@/components/ui/Modal";

export default function ContentPage() {
  const [items, setItems] = useState<ContentItem[]>([]);
  const [types, setTypes] = useState<ContentType[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal form states
  const [isOpen, setIsOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [contentTypeCode, setContentTypeCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [contentRes, typesRes] = await Promise.all([
        api.get<ContentItem[]>("/api/v1/content"),
        api.get<ContentType[]>("/api/v1/schema/content-types"),
      ]);
      setItems(Array.isArray(contentRes) ? contentRes : []);
      const typesList = Array.isArray(typesRes) ? typesRes : [];
      setTypes(typesList);
      if (typesList.length > 0) {
        setContentTypeCode(typesList[0].code);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);
    try {
      await api.post("/api/v1/content", {
        title,
        contentTypeCode,
        siteId: "00000000-0000-0000-0000-000000000000",
        siteCode: "default",
        body: {},
        metadata: {},
      });
      setTitle("");
      setIsOpen(false);
      await loadData();
    } catch (err: any) {
      setFormError(err.message || "Failed to create content item");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Content</h1>
          <p className="text-sm text-slate-400 mt-1">
            Create and manage structured content items.
          </p>
        </div>
        <Button
          onClick={() => {
            if (types.length === 0) {
              alert("Please create a Content Type first in the Schema Builder.");
              return;
            }
            setIsOpen(true);
          }}
          leftIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
            </svg>
          }
        >
          New Content
        </Button>
      </div>

      <Card padding="none">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-surface-900 border-b border-surface-800 text-slate-400">
              <tr>
                <th className="px-6 py-4 font-semibold">Title / ID</th>
                <th className="px-6 py-4 font-semibold">Type</th>
                <th className="px-6 py-4 font-semibold">Status</th>
                <th className="px-6 py-4 font-semibold">Version</th>
                <th className="px-6 py-4 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-800">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center">
                    <Spinner />
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-slate-500">
                    No content items found. Create one to get started.
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id} className="hover:bg-surface-800/50 transition-colors group">
                    <td className="px-6 py-4">
                      <p className="font-semibold text-slate-100">
                        {item.title || "Untitled"}
                      </p>
                      <p className="text-xs text-slate-500 font-mono mt-0.5 truncate max-w-[200px]">{item.id}</p>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center px-2 py-1 rounded bg-surface-800 text-xs font-mono text-slate-300">
                        {item.contentTypeCode}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <ContentStatusBadge status={item.status} />
                    </td>
                    <td className="px-6 py-4">
                      v{item.version}
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
        title="Create Content Item"
        description="Add a new content item of a specific type."
      >
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div className="p-3 text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg">
              {formError}
            </div>
          )}

          <FormField label="Title" required>
            <TextInput
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. My First Blog Post"
            />
          </FormField>

          <FormField label="Content Type" required>
            <SelectInput
              value={contentTypeCode}
              onChange={(e) => setContentTypeCode(e.target.value)}
            >
              {types.map((type) => (
                <option key={type.id} value={type.code}>
                  {type.displayName} ({type.code})
                </option>
              ))}
            </SelectInput>
          </FormField>

          <div className="flex justify-end gap-3 pt-4 border-t border-surface-800">
            <Button variant="ghost" type="button" onClick={() => setIsOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submitting}>
              Create Item
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
