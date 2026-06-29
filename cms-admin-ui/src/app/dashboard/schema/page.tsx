"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { ContentType, PagedResponse } from "@/types/cms";
import Card, { CardHeader } from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";

export default function SchemaPage() {
  const [types, setTypes] = useState<ContentType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadTypes();
  }, []);

  const loadTypes = async () => {
    setLoading(true);
    try {
      const res = await api.get<PagedResponse<ContentType>>("/api/v1/schemas/types?size=100");
      setTypes(res.items);
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
                      <p className="font-semibold text-slate-100">{t.name}</p>
                      <p className="text-xs text-slate-500 font-mono mt-0.5">{t.code}</p>
                    </td>
                    <td className="px-6 py-4">
                      <Badge variant="neutral">{t.fieldDefinitions?.length || 0} fields</Badge>
                    </td>
                    <td className="px-6 py-4 space-y-1.5">
                      <div className="flex gap-2">
                        {t.isVersioned && <Badge variant="info">Versioned</Badge>}
                        {t.isPublishable && <Badge variant="success">Publishable</Badge>}
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
    </div>
  );
}
