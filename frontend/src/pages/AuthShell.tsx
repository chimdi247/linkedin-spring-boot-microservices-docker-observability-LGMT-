import type { ReactNode } from 'react'

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-brand-500 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white text-xl font-bold text-brand-600">in</div>
        <div className="relative z-10">
          <h2 className="max-w-md text-4xl font-semibold leading-tight">Welcome to your professional community</h2>
          <p className="mt-4 max-w-md text-lg text-white/80">Share ideas, grow your network and find people with the skills you need.</p>
        </div>
        <p className="text-sm text-white/60">Microservices · Kafka · Redis · Elasticsearch</p>
        <div aria-hidden className="absolute -bottom-24 -right-24 h-96 w-96 rounded-full bg-white/10" />
        <div aria-hidden className="absolute -right-8 top-20 h-48 w-48 rounded-full bg-white/10" />
      </aside>
      <section className="flex items-center justify-center p-6">
        <div className="card w-full max-w-md p-8">
          <h1 className="text-3xl font-semibold">{title}</h1>
          <p className="mb-6 mt-1 text-sm text-muted">{subtitle}</p>
          {children}
        </div>
      </section>
    </div>
  )
}
