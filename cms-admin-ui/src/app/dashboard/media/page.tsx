"use client";

import React, { useState, useEffect } from "react";
import { api, uploadFile } from "@/lib/api";
import { MediaFile } from "@/types/cms";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";
import Modal, { FormField, TextInput } from "@/components/ui/Modal";

export default function MediaPage() {
  const [files, setFiles] = useState<MediaFile[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal form states
  const [isOpen, setIsOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [altText, setAltText] = useState("");
  const [caption, setCaption] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const loadMedia = async () => {
    setLoading(true);
    try {
      const res = await api.get<MediaFile[]>("/api/v1/media");
      setFiles(Array.isArray(res) ? res : []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMedia();
  }, []);

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) {
      setFormError("Please select a file to upload.");
      return;
    }

    setSubmitting(true);
    setFormError(null);
    try {
      const formData = new FormData();
      formData.append("file", selectedFile);
      if (altText) formData.append("altText", altText);
      if (caption) formData.append("caption", caption);

      await uploadFile("/api/v1/media/upload", formData);
      
      // Reset form states
      setSelectedFile(null);
      setAltText("");
      setCaption("");
      setIsOpen(false);
      await loadMedia();
    } catch (err: any) {
      setFormError(err.message || "Failed to upload file.");
    } finally {
      setSubmitting(false);
    }
  };

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
          onClick={() => setIsOpen(true)}
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
                  <img
                    src={file.url}
                    alt={file.alt || file.originalName}
                    className="w-full h-full object-contain p-2 group-hover:scale-105 transition-transform duration-300"
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

      <Modal
        open={isOpen}
        onClose={() => setIsOpen(false)}
        title="Upload Media Asset"
        description="Upload images, documents, or video files to your media library."
      >
        <form onSubmit={handleUploadSubmit} className="space-y-4">
          {formError && (
            <div className="p-3 text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg">
              {formError}
            </div>
          )}

          <FormField label="Select File" required>
            <input
              type="file"
              required
              onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
              className="w-full px-3 py-2 text-sm bg-surface-800 border border-surface-700 rounded-lg text-slate-100 placeholder-slate-500 
                focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent
                transition-colors"
            />
          </FormField>

          <FormField label="Alternative Text (Alt Text)">
            <TextInput
              value={altText}
              onChange={(e) => setAltText(e.target.value)}
              placeholder="e.g. Logo image or product photo description"
            />
          </FormField>

          <FormField label="Caption / Description">
            <TextInput
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              placeholder="e.g. Official NextGen CMS logo"
            />
          </FormField>

          <div className="flex justify-end gap-3 pt-4 border-t border-surface-800">
            <Button variant="ghost" type="button" onClick={() => setIsOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submitting}>
              Upload File
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
