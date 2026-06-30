"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { ContentItem } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import { ContentStatusBadge } from "@/components/ui/Badge";
import Spinner from "@/components/ui/Spinner";

export default function ContentPage() {
  const [items, setItems] = useState<ContentItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadContent() {
      try {
        const res = await api.get<ContentItem[]>("/api/v1/content");
        setItems(Array.isArray(res) ? res : []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    }
    loadContent();
  }, []);

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
                        {String(item.payload.title || item.payload.name || "Untitled")}
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
    </div>
  );
}
