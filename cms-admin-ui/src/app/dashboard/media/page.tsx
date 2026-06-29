"use client";

import React, { useState, useEffect } from "react";
import Image from "next/image";
import { api } from "@/lib/api";
import { MediaFile, PagedResponse } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";

export default function MediaPage() {
  const [files, setFiles] = useState<MediaFile[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadMedia() {
      try {
        const res = await api.get<PagedResponse<MediaFile>>("/api/v1/media?size=50");
        setFiles(res.items);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    }
    loadMedia();
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Media Library</h1>
          <p className="text-sm text-slate-400 mt-1">
            Upload and manage assets, images, and documents.
          </p>
        </div>
        <Button
          leftIcon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
          }
        >
          Upload
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-20">
          <Spinner size="lg" />
        </div>
      ) : files.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-20 text-center border-dashed">
          <div className="w-16 h-16 rounded-full bg-surface-800 flex items-center justify-center mb-4">
            <svg className="w-8 h-8 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </div>
          <h3 className="text-lg font-medium text-slate-200">No media yet</h3>
          <p className="text-slate-400 max-w-sm mt-1 mb-6">
            Upload images, PDFs, or videos to use them across your content items.
          </p>
          <Button variant="secondary">Browse Files</Button>
        </Card>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {files.map((file) => (
            <Card key={file.id} padding="none" hover className="overflow-hidden group cursor-pointer border-surface-800 bg-surface-900 flex flex-col h-full">
              <div className="aspect-square relative bg-surface-950 flex items-center justify-center p-2 border-b border-surface-800">
                {file.mimeType.startsWith("image/") ? (
                  <Image
                    src={process.env.NEXT_PUBLIC_API_URL + file.url}
                    alt={file.alt || file.originalName}
                    fill
                    className="object-contain p-2 group-hover:scale-105 transition-transform duration-300"
                  />
                ) : (
                  <svg className="w-12 h-12 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                  </svg>
                )}
                {/* Overlay actions */}
                <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <Button variant="secondary" size="sm">View</Button>
                </div>
              </div>
              <div className="p-3 text-center flex-1 flex flex-col justify-center">
                <p className="text-xs font-medium text-slate-200 truncate w-full" title={file.originalName}>
                  {file.originalName}
                </p>
                <p className="text-[10px] text-slate-500 mt-0.5">
                  {(file.size / 1024).toFixed(1)} KB
                </p>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
