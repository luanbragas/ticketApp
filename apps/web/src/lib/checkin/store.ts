/**
 * Estado da portaria guardado no aparelho (IndexedDB): lista de ingressos e fila de leituras a
 * sincronizar. Sobrevive a recarregar a página sem internet (ADR-010).
 */
import type { SyncItem } from "@/lib/api/checkin"

import type { GateEntry } from "./engine"

const DB = "festa-checkin"
const STORE = "events"

export type SavedGate = {
  eventId: string
  eventName: string
  entries: GateEntry[]
  queue: SyncItem[]
  /** Hora da última lista baixada do servidor (ISO). */
  downloadedAt: string
}

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1)
    request.onupgradeneeded = () => {
      request.result.createObjectStore(STORE, { keyPath: "eventId" })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function run<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest,
): Promise<T> {
  const db = await open()
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode)
      const request = action(tx.objectStore(STORE))
      tx.oncomplete = () => resolve(request.result as T)
      tx.onerror = () => reject(tx.error)
    })
  } finally {
    db.close()
  }
}

export async function loadGate(eventId: string): Promise<SavedGate | null> {
  try {
    return (
      (await run<SavedGate | undefined>("readonly", (s) => s.get(eventId))) ??
      null
    )
  } catch {
    return null
  }
}

export async function saveGate(gate: SavedGate): Promise<void> {
  try {
    await run("readwrite", (s) => s.put(gate))
  } catch {
    // Sem IndexedDB (aba privada): a portaria funciona enquanto a página estiver aberta.
  }
}

/** Depois do evento: tira nomes e hashes do aparelho. */
export async function clearGate(eventId: string): Promise<void> {
  try {
    await run("readwrite", (s) => s.delete(eventId))
  } catch {
    // Nada guardado.
  }
}

/** Id do aparelho para o servidor separar leituras de cada portaria. */
export function deviceId(): string {
  const key = "festa:device"
  try {
    const saved = localStorage.getItem(key)
    if (saved) return saved
    const fresh = `d-${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`
    localStorage.setItem(key, fresh)
    return fresh
  } catch {
    return `d-${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`
  }
}
