"use client"

import { CameraIcon, SearchIcon, WifiIcon, WifiOffIcon } from "lucide-react"
import Link from "next/link"
import { useCallback, useEffect, useRef, useState } from "react"

import { getManifest, syncCheckins, type SyncItem } from "@/lib/api/checkin"
import {
  admit,
  buildGate,
  counts,
  entries,
  refresh,
  searchByName,
  sha256Hex,
  tokenFromQr,
  type Gate,
  type GateEntry,
  type Verdict,
} from "@/lib/checkin/engine"
import { clearGate, deviceId, loadGate, saveGate } from "@/lib/checkin/store"
import { cn } from "@/lib/utils"

import { QrScanner } from "./qr-scanner"

const SYNC_EVERY_MS = 5_000
const REFRESH_EVERY_MS = 60_000
const SYNC_BATCH = 200
const RESULT_MS = 1_800

const TIME = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Sao_Paulo",
})

/**
 * Portaria offline-first (PLAN.md M8, ADR-010): baixa a lista, confere cada QR no aparelho e sincroniza a
 * fila quando há internet. Funciona em modo avião depois de aberta uma vez.
 */
export function GateApp({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const [gate, setGate] = useState<Gate | null>(null)
  const [eventName, setEventName] = useState("")
  const [queue, setQueue] = useState<SyncItem[]>([])
  const [downloadedAt, setDownloadedAt] = useState<string | null>(null)
  const [online, setOnline] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [verdict, setVerdict] = useState<Verdict | null>(null)
  const [mode, setMode] = useState<"scan" | "search">("scan")
  const [conflicts, setConflicts] = useState(0)
  // Refs: o leitor de QR e o timer de sincronização sempre veem o estado atual.
  const gateRef = useRef<Gate | null>(null)
  const queueRef = useRef<SyncItem[]>([])
  const syncing = useRef(false)
  const device = useRef<string>("")

  const persist = useCallback(
    (nextGate: Gate, nextQueue: SyncItem[], name: string, at: string) =>
      saveGate({
        eventId,
        eventName: name,
        entries: entries(nextGate),
        queue: nextQueue,
        downloadedAt: at,
      }),
    [eventId],
  )

  const apply = useCallback((nextGate: Gate, nextQueue: SyncItem[]) => {
    gateRef.current = nextGate
    queueRef.current = nextQueue
    setGate(nextGate)
    setQueue(nextQueue)
  }, [])

  const download = useCallback(async () => {
    const manifest = await getManifest(organizationId, eventId)
    const current = gateRef.current ?? buildGate([])
    const nextGate = refresh(current, manifest.tickets, queueRef.current)
    apply(nextGate, queueRef.current)
    setEventName(manifest.eventName)
    setDownloadedAt(manifest.generatedAt)
    await persist(
      nextGate,
      queueRef.current,
      manifest.eventName,
      manifest.generatedAt,
    )
  }, [apply, eventId, organizationId, persist])

  const sync = useCallback(async () => {
    if (syncing.current || queueRef.current.length === 0 || !navigator.onLine)
      return
    syncing.current = true
    const batch = queueRef.current.slice(0, SYNC_BATCH)
    try {
      const response = await syncCheckins(
        organizationId,
        eventId,
        device.current,
        batch,
      )
      setConflicts(
        (n) =>
          n + response.results.filter((r) => r.outcome !== "ACCEPTED").length,
      )
      const rest = queueRef.current.slice(batch.length)
      apply(gateRef.current!, rest)
      await persist(
        gateRef.current!,
        rest,
        eventName,
        downloadedAt ?? new Date().toISOString(),
      )
    } catch {
      // Sem rede ou servidor fora: a fila fica e tenta de novo no próximo ciclo.
    } finally {
      syncing.current = false
    }
  }, [apply, downloadedAt, eventId, eventName, organizationId, persist])

  // Abre: registra o service worker, lê o que está no aparelho e baixa a lista se houver internet.
  useEffect(() => {
    device.current = deviceId()
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker
        .register("/checkin-sw.js", { scope: "/checkin/" })
        .catch(() => {})
    }
    const updateOnline = () => setOnline(navigator.onLine)
    updateOnline()
    window.addEventListener("online", updateOnline)
    window.addEventListener("offline", updateOnline)
    let cancelled = false
    ;(async () => {
      const saved = await loadGate(eventId)
      if (cancelled) return
      if (saved) {
        apply(buildGate(saved.entries), saved.queue)
        setEventName(saved.eventName)
        setDownloadedAt(saved.downloadedAt)
      }
      if (navigator.onLine) {
        try {
          await download()
        } catch (error) {
          if (!saved) {
            setLoadError(
              error instanceof Error
                ? error.message
                : "Não deu para baixar a lista.",
            )
          }
        }
      } else if (!saved) {
        setLoadError(
          "Sem internet e sem lista neste aparelho. Conecte uma vez para baixar.",
        )
      }
    })()
    return () => {
      cancelled = true
      window.removeEventListener("online", updateOnline)
      window.removeEventListener("offline", updateOnline)
    }
    // Só ao abrir a tela.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId])

  useEffect(() => {
    const syncTimer = setInterval(sync, SYNC_EVERY_MS)
    const refreshTimer = setInterval(() => {
      if (navigator.onLine && queueRef.current.length === 0)
        download().catch(() => {})
    }, REFRESH_EVERY_MS)
    return () => {
      clearInterval(syncTimer)
      clearInterval(refreshTimer)
    }
  }, [download, sync])

  useEffect(() => {
    if (!verdict) return
    const timer = setTimeout(() => setVerdict(null), RESULT_MS)
    return () => clearTimeout(timer)
  }, [verdict])

  const decide = useCallback(
    (key: { tokenHash: string } | { ticketId: string }) => {
      const current = gateRef.current
      if (!current) return
      const { verdict: result, queued } = admit(current, key, new Date())
      setVerdict(result)
      navigator.vibrate?.(result.kind === "ADMITTED" ? 80 : [60, 60, 60])
      if (queued) {
        const nextQueue = [...queueRef.current, queued]
        apply(current, nextQueue)
        persist(
          current,
          nextQueue,
          eventName,
          downloadedAt ?? new Date().toISOString(),
        )
        sync()
      } else {
        setGate(current)
      }
    },
    [apply, downloadedAt, eventName, persist, sync],
  )

  const onQr = useCallback(
    async (raw: string) => {
      const token = tokenFromQr(raw)
      if (!token) {
        setVerdict({ kind: "INVALID", entry: null })
        return
      }
      decide({ tokenHash: await sha256Hex(token) })
    },
    [decide],
  )

  if (loadError && !gate) {
    return (
      <Shell eventName="Portaria" online={online} pending={0}>
        <p
          role="alert"
          className="px-5 pt-10 text-center text-muted-foreground"
        >
          {loadError}
        </p>
      </Shell>
    )
  }
  if (!gate) {
    return (
      <Shell eventName="Portaria" online={online} pending={0}>
        <p className="px-5 pt-10 text-center text-muted-foreground">
          Baixando a lista…
        </p>
      </Shell>
    )
  }

  const { inside, total } = counts(gate)

  return (
    <Shell eventName={eventName} online={online} pending={queue.length}>
      <div className="grid grid-cols-2 border-b">
        <div className="border-r px-5 py-3">
          <p className="text-xs font-bold text-muted-foreground">Entraram</p>
          <p className="font-display text-4xl leading-none font-black">
            {inside}
            <span className="text-xl text-muted-foreground"> / {total}</span>
          </p>
        </div>
        <div className="px-5 py-3 text-sm text-muted-foreground">
          <p>
            {queue.length > 0
              ? `${queue.length} na fila${online ? ", enviando" : " (sem internet)"}`
              : "Tudo sincronizado"}
          </p>
          {conflicts > 0 && <p>{conflicts} lidos antes em outro aparelho</p>}
          {downloadedAt && (
            <p>Lista de {TIME.format(new Date(downloadedAt))}</p>
          )}
        </div>
      </div>

      <div role="tablist" className="grid grid-cols-2 border-b">
        <Tab active={mode === "scan"} onClick={() => setMode("scan")}>
          <CameraIcon className="size-4" aria-hidden /> Ler QR
        </Tab>
        <Tab active={mode === "search"} onClick={() => setMode("search")}>
          <SearchIcon className="size-4" aria-hidden /> Buscar nome
        </Tab>
      </div>

      {mode === "scan" ? (
        <QrScanner onRead={onQr} paused={verdict !== null} />
      ) : (
        <Search
          gate={gate}
          onPick={(entry) => decide({ ticketId: entry.ticketId })}
        />
      )}

      {verdict && (
        <VerdictOverlay verdict={verdict} onClose={() => setVerdict(null)} />
      )}

      <div className="mt-auto grid gap-2 px-5 py-6 text-center text-xs text-muted-foreground">
        <p>Depois do evento, apague a lista deste aparelho.</p>
        <button
          type="button"
          className="mx-auto min-h-11 font-bold underline underline-offset-4 disabled:opacity-40"
          disabled={queue.length > 0}
          onClick={async () => {
            await clearGate(eventId)
            apply(buildGate([]), [])
            setLoadError("Lista apagada deste aparelho.")
            setGate(null)
          }}
        >
          {queue.length > 0
            ? "Sincronize antes de apagar"
            : "Apagar lista do aparelho"}
        </button>
      </div>
    </Shell>
  )
}

function Shell({
  eventName,
  online,
  pending,
  children,
}: {
  eventName: string
  online: boolean
  pending: number
  children: React.ReactNode
}) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col bg-background">
      <header className="flex h-14 items-center justify-between gap-3 border-b px-5">
        <Link
          href="/painel/eventos"
          className="min-w-0 truncate font-display text-xl font-black uppercase"
        >
          {eventName}
        </Link>
        <span
          className={cn(
            "flex shrink-0 items-center gap-1.5 px-2 py-1 text-xs font-extrabold uppercase",
            online ? "bg-secondary" : "bg-[#ffb020] text-black",
          )}
          aria-live="polite"
        >
          {online ? (
            <WifiIcon className="size-3.5" aria-hidden />
          ) : (
            <WifiOffIcon className="size-3.5" aria-hidden />
          )}
          {online ? "Online" : `Offline${pending ? ` · ${pending}` : ""}`}
        </span>
      </header>
      {children}
    </div>
  )
}

function Tab({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "-mb-px flex min-h-12 items-center justify-center gap-2 border-b-2 text-sm font-extrabold",
        active ? "border-primary" : "border-transparent text-muted-foreground",
      )}
    >
      {children}
    </button>
  )
}

function Search({
  gate,
  onPick,
}: {
  gate: Gate
  onPick: (entry: GateEntry) => void
}) {
  const [query, setQuery] = useState("")
  const results = searchByName(gate, query)
  return (
    <div className="grid gap-3 px-5 py-4">
      <label htmlFor="busca-portaria" className="sr-only">
        Nome do titular
      </label>
      <input
        id="busca-portaria"
        type="search"
        autoFocus
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Nome do titular"
        className="h-12 border-b border-input bg-transparent text-lg outline-none focus-visible:border-primary"
      />
      <ul>
        {results.map((entry) => (
          <li key={entry.ticketId} className="border-b">
            <button
              type="button"
              onClick={() => onPick(entry)}
              className="flex min-h-14 w-full items-center justify-between gap-3 py-2 text-left"
            >
              <span className="min-w-0">
                <span className="block truncate font-bold">
                  {entry.holderName}
                </span>
                <span className="block text-sm text-muted-foreground">
                  {entry.typeName}
                  {entry.halfPrice ? " · meia" : ""}
                </span>
              </span>
              <span className="shrink-0 text-xs font-extrabold uppercase">
                {entry.status === "CHECKED_IN"
                  ? "Já entrou"
                  : entry.status === "VALID"
                    ? "Liberar"
                    : "Inválido"}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {query.trim().length >= 2 && results.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Ninguém com esse nome. Busca por CPF fica na lista de participantes
          (precisa de internet).
        </p>
      )}
    </div>
  )
}

const VERDICT_STYLE: Record<Verdict["kind"], string> = {
  ADMITTED: "bg-primary text-primary-foreground",
  ALREADY_IN: "bg-[#ffb020] text-black",
  INVALID: "bg-destructive text-white",
}

function VerdictOverlay({
  verdict,
  onClose,
}: {
  verdict: Verdict
  onClose: () => void
}) {
  const title =
    verdict.kind === "ADMITTED"
      ? "Pode entrar"
      : verdict.kind === "ALREADY_IN"
        ? "Já entrou"
        : "Inválido"
  return (
    <button
      type="button"
      onClick={onClose}
      role="alert"
      className={cn(
        "fixed inset-0 z-50 flex flex-col items-center justify-center gap-3 px-6 text-center",
        VERDICT_STYLE[verdict.kind],
      )}
    >
      <span className="font-display text-7xl leading-[0.85] font-black uppercase">
        {title}
      </span>
      {verdict.entry && (
        <>
          <span className="font-display text-4xl leading-none font-black uppercase">
            {verdict.entry.holderName}
          </span>
          <span className="text-lg font-bold">
            {verdict.entry.typeName}
            {verdict.entry.halfPrice ? " · MEIA: confira o documento" : ""}
          </span>
        </>
      )}
      {verdict.kind === "ALREADY_IN" && verdict.at && (
        <span className="text-lg font-bold">
          às {TIME.format(new Date(verdict.at))}
        </span>
      )}
      {verdict.kind === "INVALID" && !verdict.entry && (
        <span className="text-lg font-bold">
          QR de outra festa ou falsificado
        </span>
      )}
      <span className="mt-6 text-sm opacity-80">Toque para continuar</span>
    </button>
  )
}
