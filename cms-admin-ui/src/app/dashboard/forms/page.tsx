"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { FormDefinition } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";
import Modal, { FormField, TextInput, TextareaInput } from "@/components/ui/Modal";

export default function FormsPage() {
  const [forms, setForms] = useState<FormDefinition[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal form states
  const [isOpen, setIsOpen] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [schema, setSchema] = useState('{\n  "type": "object",\n  "properties": {\n    "name": { "type": "string" },\n    "email": { "type": "string", "format": "email" }\n  }\n}');
  const [uiSchema, setUiSchema] = useState('{\n  "ui:order": ["name", "email"]\n}');
  const [submitAction, setSubmitAction] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const loadForms = async () => {
    setLoading(true);
    try {
      const res = await api.get<FormDefinition[]>("/api/v1/forms/definitions");
      setForms(Array.isArray(res) ? res : []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadForms();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);

    // Validate JSON Schemas
    try {
      JSON.parse(schema);
    } catch {
      setFormError("Schema must be valid JSON.");
      setSubmitting(false);
      return;
    }
    try {
      JSON.parse(uiSchema);
    } catch {
      setFormError("UI Schema must be valid JSON.");
      setSubmitting(false);
      return;
    }

    try {
      await api.post("/api/v1/forms/definitions", {
        code,
        name,
        description,
        schema,
        uiSchema,
        submitAction,
        isActive: true,
      });
      setCode("");
      setName("");
      setDescription("");
      setSchema('{\n  "type": "object",\n  "properties": {\n    "name": { "type": "string" },\n    "email": { "type": "string", "format": "email" }\n  }\n}');
      setUiSchema('{\n  "ui:order": ["name", "email"]\n}');
      setSubmitAction("");
      setIsOpen(false);
      await loadForms();
    } catch (err: any) {
      setFormError(err.message || "Failed to create form definition.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Forms</h1>
          <p className="text-sm text-slate-400 mt-1">
            Manage public form endpoints and submissions.
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
          Create Form
        </Button>
      </div>

      <Card padding="none">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-surface-900 border-b border-surface-800 text-slate-400">
              <tr>
                <th className="px-6 py-4 font-semibold">Form Name / Code</th>
                <th className="px-6 py-4 font-semibold">Status</th>
                <th className="px-6 py-4 font-semibold">Endpoint</th>
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
              ) : forms.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center text-slate-500">
                    No forms defined yet.
                  </td>
                </tr>
              ) : (
                forms.map((form) => (
                  <tr key={form.id} className="hover:bg-surface-800/50 transition-colors group">
                    <td className="px-6 py-4">
                      <p className="font-semibold text-slate-100">{form.name}</p>
                      <p className="text-xs text-slate-500 font-mono mt-0.5">{form.code}</p>
                    </td>
                    <td className="px-6 py-4">
                      <Badge variant={form.isActive ? "success" : "neutral"} dot>
                        {form.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </td>
                    <td className="px-6 py-4">
                      <code className="text-xs bg-surface-800 px-2 py-1 rounded text-slate-400">
                        POST /api/v1/forms/{form.code}/submit
                      </code>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <Button variant="ghost" size="sm">Submissions</Button>
                      <Button variant="ghost" size="sm" className="ml-2">Edit</Button>
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
        title="Create Form Definition"
        description="Add a new form endpoint for submissions."
        size="lg"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          {formError && (
            <div className="p-3 text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg">
              {formError}
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <FormField label="Form Name" required>
              <TextInput
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Contact Form"
              />
            </FormField>

            <FormField label="Form Code" required>
              <TextInput
                required
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="e.g. contact_form (lowercase, no spaces)"
                pattern="^[a-z0-9_]+$"
                title="Only lowercase letters, numbers, and underscores are allowed"
              />
            </FormField>
          </div>

          <FormField label="Description">
            <TextInput
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the purpose of this form..."
            />
          </FormField>

          <FormField label="JSON Schema" required>
            <TextareaInput
              required
              value={schema}
              onChange={(e) => setSchema(e.target.value)}
              rows={4}
              className="font-mono text-xs"
            />
          </FormField>

          <FormField label="UI Schema (JSON)" required>
            <TextareaInput
              required
              value={uiSchema}
              onChange={(e) => setUiSchema(e.target.value)}
              rows={3}
              className="font-mono text-xs"
            />
          </FormField>

          <FormField label="Submit Action / Webhook URL">
            <TextInput
              value={submitAction}
              onChange={(e) => setSubmitAction(e.target.value)}
              placeholder="e.g. http://webhook.site/..."
            />
          </FormField>

          <div className="flex justify-end gap-3 pt-4 border-t border-surface-800">
            <Button variant="ghost" type="button" onClick={() => setIsOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submitting}>
              Create Form
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
