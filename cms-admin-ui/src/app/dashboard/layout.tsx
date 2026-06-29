"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import Sidebar from "@/components/layout/Sidebar";
import Topbar from "@/components/layout/Topbar";
import { FullPageSpinner } from "@/components/ui/Spinner";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isLoggedIn, isLoading } = useAuth();
  const router = useRouter();

  // Route guard
  useEffect(() => {
    if (!isLoading && !isLoggedIn) {
      router.push("/login");
    }
  }, [isLoading, isLoggedIn, router]);

  // While restoring session, show spinner
  if (isLoading) {
    return <FullPageSpinner />;
  }

  // If redirecting, render nothing to avoid flash
  if (!isLoggedIn) {
    return null;
  }

  return (
    <div className="min-h-screen bg-surface-950 flex">
      <Sidebar />
      <div className="flex-1 ml-[260px] flex flex-col min-h-screen transition-all duration-300">
        <Topbar />
        <main className="flex-1 p-6 mt-16 overflow-x-hidden">
          {/* Main content container with max-width and centering */}
          <div className="max-w-7xl mx-auto w-full animate-fade-in">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
