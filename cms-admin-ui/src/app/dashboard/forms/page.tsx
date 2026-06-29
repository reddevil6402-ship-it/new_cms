"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { FormDefinition } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";

export default function FormsPage() {
  const [forms, setForms] = useState<FormDefinition[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadForms() {
      try {
        // The API returns an array directly for /definitions currently in the form service
        const res = await api.get<FormDefinition[]>("/api/v1/forms/definitions");
        setForms(Array.isArray(res) ? res : []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    }
    loadForms();
  }, []);

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
    </div>
  );
}
