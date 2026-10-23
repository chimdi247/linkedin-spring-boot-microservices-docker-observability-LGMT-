import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { AlertCircle, CheckCircle2, X } from 'lucide-react'

type Kind = 'success' | 'error'
interface ToastItem { id: number; kind: Kind; message: string }
interface ToastApi { toast: (message: string, kind?: Kind) => void }

const Ctx = createContext<ToastApi>({ toast: () => undefined })
export const useToast = () => useContext(Ctx)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const dismiss = useCallback((id: number) => setItems((l) => l.filter((t) => t.id !== id)), [])
  const toast = useCallback((message: string, kind: Kind = 'success') => {
    const id = Date.now() + Math.random()
    setItems((l) => [...l.slice(-3), { id, kind, message }])
    window.setTimeout(() => dismiss(id), 4500)
  }, [dismiss])
  const value = useMemo(() => ({ toast }), [toast])

  return (
    <Ctx.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            data-testid="toast"
            role="status"
            className={`pointer-events-auto flex max-w-md animate-toast-in items-center gap-3 rounded-lg px-4 py-3 text-sm text-white shadow-lg ${t.kind === 'error' ? 'bg-red-700' : 'bg-neutral-900'}`}
          >
            {t.kind === 'error' ? <AlertCircle size={18} /> : <CheckCircle2 size={18} className="text-emerald-400" />}
            <span className="flex-1">{t.message}</span>
            <button aria-label="Dismiss" onClick={() => dismiss(t.id)} className="opacity-70 hover:opacity-100"><X size={16} /></button>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  )
}
