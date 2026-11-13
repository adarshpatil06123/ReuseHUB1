import type { ReactNode } from "react";
import AppHeader from "@/components/AppHeader";
import { requireAuth } from "@/lib/server-auth";
import { NotificationProvider } from "@/context/NotificationContext";
import { SearchProvider } from "@/context/SearchContext";

type ProtectedLayoutProps = {
  children: ReactNode;
};

export default async function ProtectedLayout({ children }: ProtectedLayoutProps) {
  await requireAuth();
  return (
    <NotificationProvider>
      <AppHeader />
      {children}
    </NotificationProvider>
  );
}
