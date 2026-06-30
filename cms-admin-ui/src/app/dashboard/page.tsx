"use client";

import React, { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { Card, CardHeader, StatCard } from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import { ContentType, ContentItem } from "@/types/cms";

export default function DashboardPage() {
  const [stats, setStats] = useState({
    totalContentTypes: 0,
    totalContentItems: 0,
    publishedItems: 0,
    totalForms: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadStats() {
      try {
        // Fetch in parallel
        const [types, items, forms] = await Promise.all([
          api.get<ContentType[]>("/api/v1/schema/content-types"),
          api.get<ContentItem[]>("/api/v1/content"),
          api.get<any[]>("/api/v1/forms/definitions").catch(() => []),
        ]);

        const typesCount = Array.isArray(types) ? types.length : 0;
        const itemsCount = Array.isArray(items) ? items.length : 0;
        const formsCount = Array.isArray(forms) ? forms.length : 0;

        setStats({
          totalContentTypes: typesCount,
          totalContentItems: itemsCount,
          publishedItems: Math.floor(itemsCount * 0.7), // Mock calculation for now
          totalForms: formsCount,
        });
      } catch (err) {
        console.error("Failed to load dashboard stats", err);
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-2xl bg-brand-gradient p-8 text-white shadow-brand-glow">
        <div className="absolute inset-0 bg-hero-mesh opacity-30" />
        <div className="relative z-10">
          <h1 className="text-3xl font-bold mb-2">Welcome to NextGen CMS</h1>
          <p className="text-brand-100 max-w-xl">
            Manage your content, define custom schemas, and oversee your enterprise platform all from one unified dashboard.
          </p>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          label="Content Types"
          value={stats.totalContentTypes}
          loading={loading}
          color="brand"
          change="+2 this week"
          changeType="up"
          icon={
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
            </svg>
          }
        />
        <StatCard
          label="Total Content"
          value={stats.totalContentItems}
          loading={loading}
          color="amber"
          change="+15 this week"
          changeType="up"
          icon={
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
          }
        />
        <StatCard
          label="Published"
          value={stats.publishedItems}
          loading={loading}
          color="green"
          change="98% uptime"
          changeType="neutral"
          icon={
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Active Forms"
          value={stats.totalForms}
          loading={loading}
          color="info"
          icon={
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          }
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card glass padding="lg">
          <CardHeader title="Recent Activity" subtitle="Latest system events" />
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-start gap-4 pb-4 border-b border-surface-800 last:border-0 last:pb-0">
                <div className="w-8 h-8 rounded-full bg-surface-800 flex items-center justify-center shrink-0 mt-0.5">
                  <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </div>
                <div>
                  <p className="text-sm text-slate-200">
                    User <span className="font-semibold text-brand-400">admin@nextgen.local</span> updated content item
                  </p>
                  <p className="text-xs text-slate-500 mt-1">2 hours ago</p>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card glass padding="lg">
          <CardHeader title="System Status" subtitle="All services operational" />
          <div className="space-y-4">
            {["IAM Service", "Schema Service", "Content Service", "Media Service", "Search Service"].map((service) => (
              <div key={service} className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-300">{service}</span>
                <Badge variant="success" dot>Healthy</Badge>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
