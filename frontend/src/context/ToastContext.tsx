"use client";

import {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState
} from "react";

export type ToastType = "success" | "error" | "info";

type ToastMessage = {
  id: number;
  message: string;
  type: ToastType;
};

type ToastContextValue = {
  showToast: (message: string, type?: ToastType) => void;
};

const ToastContext = createContext<ToastContextValue>({
  showToast: () => {}
});

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  const nextId = useRef(0);
  const lastToast = useRef<{ key: string; at: number } | null>(null);

  const dismissToast = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer) clearTimeout(timer);
    timers.current.delete(id);
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback((message: string, type: ToastType = "info") => {
    const key = `${type}:${message}`;
    const now = Date.now();
    if (lastToast.current?.key === key && now - lastToast.current.at < 800) return;
    lastToast.current = { key, at: now };
    const id = ++nextId.current;
    setToasts((current) => [...current.slice(-3), { id, message, type }]);
    timers.current.set(id, setTimeout(() => dismissToast(id), 4500));
  }, [dismissToast]);

  useEffect(() => {
    const handleApiToast = (event: Event) => {
      const { message, type } = (event as CustomEvent<{ message: string; type: ToastType }>).detail;
      showToast(message, type);
    };
    window.addEventListener("reusehub:toast", handleApiToast);
    return () => window.removeEventListener("reusehub:toast", handleApiToast);
  }, [showToast]);

  useEffect(() => {
    const activeTimers = timers.current;
    return () => {
      activeTimers.forEach(clearTimeout);
      activeTimers.clear();
    };
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="global-toast-container" aria-live="polite" aria-relevant="additions">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`global-toast ${toast.type}`}
            role={toast.type === "error" ? "alert" : "status"}
          >
            <span>{toast.message}</span>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              aria-label="Dismiss notification"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
