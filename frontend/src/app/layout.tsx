import "./globals.css";
import "leaflet/dist/leaflet.css";
import type { Metadata } from "next";
import { ToastProvider } from "@/context/ToastContext";
import { SearchProvider } from "@/context/SearchContext";

export const metadata: Metadata = {
  title: "ReuseHub",
  description: "Campus-only resource sharing and marketplace"
};

export default function RootLayout({
  children
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <ToastProvider>
          <SearchProvider>{children}</SearchProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
