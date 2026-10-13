"use client"

import { useState } from "react"

import { formatCents } from "@/lib/money"

type Day = { date: string; tickets: number; revenueCents: number }

const DAY_LABEL = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  timeZone: "UTC",
})

/** "2026-11-14" (data de São Paulo já calculada na API) → "14/11". */
function dayLabel(date: string): string {
  return DAY_LABEL.format(new Date(`${date}T12:00:00Z`))
}

/**
 * Ingressos vendidos por dia: uma série só (o título diz o que é, sem legenda), barras finas com 2px de
 * respiro, eixo discreto, rótulo só no maior dia, dica ao passar o dedo/mouse e tabela para leitor de tela.
 */
export function SalesByDay({ days }: { days: Day[] }) {
  const [active, setActive] = useState<number | null>(null)
  const max = Math.max(1, ...days.map((d) => d.tickets))
  const peak = days.findIndex((d) => d.tickets === max)
  const shown = active ?? null

  return (
    <section aria-labelledby="vendas-dia" className="grid gap-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2
          id="vendas-dia"
          className="font-display text-2xl font-black uppercase"
        >
          Ingressos por dia
        </h2>
        <p
          className="min-h-5 text-right text-sm text-muted-foreground"
          aria-live="polite"
        >
          {shown !== null
            ? `${dayLabel(days[shown].date)}: ${days[shown].tickets} · ${formatCents(days[shown].revenueCents)}`
            : ""}
        </p>
      </div>
      {days.length === 0 ? (
        <p className="border-y py-8 text-center text-sm text-muted-foreground">
          As vendas pagas aparecem aqui, dia a dia.
        </p>
      ) : (
        <>
          <div
            aria-hidden
            className="flex h-40 items-end gap-0.5 border-b border-input"
            onMouseLeave={() => setActive(null)}
          >
            {days.map((day, i) => (
              <button
                key={day.date}
                type="button"
                tabIndex={-1}
                onMouseEnter={() => setActive(i)}
                onFocus={() => setActive(i)}
                onClick={() => setActive(active === i ? null : i)}
                className="group relative flex h-full min-w-1 flex-1 items-end"
              >
                <span
                  className={`block w-full rounded-t-[2px] ${active === i ? "bg-foreground" : "bg-primary"}`}
                  style={{
                    height: `${Math.max(2, (day.tickets / max) * 100)}%`,
                  }}
                />
                {i === peak && active === null && (
                  <span className="absolute -top-5 left-1/2 -translate-x-1/2 text-xs font-bold">
                    {day.tickets}
                  </span>
                )}
              </button>
            ))}
          </div>
          <div
            aria-hidden
            className="flex justify-between text-xs text-muted-foreground"
          >
            <span>{dayLabel(days[0].date)}</span>
            {days.length > 1 && (
              <span>{dayLabel(days[days.length - 1].date)}</span>
            )}
          </div>
          <table className="sr-only">
            <caption>Ingressos vendidos por dia</caption>
            <thead>
              <tr>
                <th scope="col">Dia</th>
                <th scope="col">Ingressos</th>
                <th scope="col">Receita</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <td>{dayLabel(day.date)}</td>
                  <td>{day.tickets}</td>
                  <td>{formatCents(day.revenueCents)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}
