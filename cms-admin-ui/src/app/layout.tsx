import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/providers/AuthProvider";

export const metadata: Metadata = {
  title: {
    default: "NextGen CMS",
    template: "%s | NextGen CMS",
  },
  description:
    "NextGen Enterprise CMS — A powerful microservices-based content management platform built for the Indian government and enterprise market.",
  keywords: ["CMS", "content management", "enterprise", "India", "government"],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
      </head>
      <body className="bg-surface-950 text-slate-100 font-sans antialiased">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
