"use client";

import React from "react";
import Card, { CardHeader } from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import { TextInput, SelectInput } from "@/components/ui/Modal";
import { useAuth } from "@/providers/AuthProvider";

export default function SettingsPage() {
  const { user } = useAuth();

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">Settings</h1>
        <p className="text-sm text-slate-400 mt-1">
          Manage your tenant preferences and API configurations.
        </p>
      </div>

      <Card padding="lg">
        <CardHeader 
          title="Tenant Profile" 
          subtitle="Basic information about your current workspace." 
        />
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Tenant Code</label>
            <TextInput disabled value={user?.tcode || ""} className="opacity-70 bg-surface-900" />
            <p className="text-xs text-slate-500 mt-1">This code is used in API requests to identify your workspace.</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Primary Contact Email</label>
            <TextInput defaultValue="admin@nextgen.local" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Timezone</label>
            <SelectInput defaultValue="Asia/Kolkata">
              <option value="UTC">UTC (Universal Coordinated Time)</option>
              <option value="Asia/Kolkata">Asia/Kolkata (IST)</option>
              <option value="America/New_York">America/New_York (EST)</option>
            </SelectInput>
          </div>
          <div className="pt-4 flex justify-end">
            <Button>Save Changes</Button>
          </div>
        </div>
      </Card>

      <Card padding="lg" className="border-red-500/20">
        <CardHeader 
          title="Danger Zone" 
          subtitle="Irreversible actions for this workspace." 
        />
        <div className="flex items-center justify-between p-4 bg-red-500/5 rounded-lg border border-red-500/10">
          <div>
            <h4 className="text-sm font-semibold text-slate-200">Suspend Workspace</h4>
            <p className="text-xs text-slate-400 mt-0.5">Pause all API access and background jobs.</p>
          </div>
          <Button variant="danger">Suspend</Button>
        </div>
      </Card>
    </div>
  );
}
