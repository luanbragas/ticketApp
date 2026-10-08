"use client"

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { ImageUpIcon } from "lucide-react"
import { useRouter } from "next/navigation"
import { useEffect, useId, useState } from "react"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  getEvent,
  requestUploadUrl,
  setFlyer,
  updateEvent,
  uploadToStorage,
} from "@/lib/api/events"
import { ApiError } from "@/lib/api/client"
import type { EventDetail } from "@/lib/api/types"
import {
  coverLayout,
  dominantAccent,
  ensureReadableOnBlack,
  PLATFORM_ACCENT,
  textOn,
} from "@/lib/color"
import { cn } from "@/lib/utils"

const ACCEPTED = ["image/jpeg", "image/png", "image/webp"]
const MAX_BYTES = 5 * 1024 * 1024

const LAYOUT_LABELS = {
  story: "Story 9:16 · a capa ocupa o topo inteiro",
  feed: "Feed 4:5 · a capa aparece inteira",
  wide: "Deitado ou quadrado · capa com fundo desfocado",
} as const

/** Lê dimensões e a cor marcante do arquivo, no próprio navegador, antes de enviar. */
async function inspect(file: File) {
  const bitmap = await createImageBitmap(file)
  const canvas = document.createElement("canvas")
  canvas.width = 64
  canvas.height = 64
  const context = canvas.getContext("2d")!
  context.drawImage(bitmap, 0, 0, 64, 64)
  const accent = dominantAccent(context.getImageData(0, 0, 64, 64).data)
  const result = { width: bitmap.width, height: bitmap.height, accent }
  bitmap.close()
  return result
}

export function AppearanceStep({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId),
  })
  if (event.isPending) {
    return (
      <div aria-busy="true" className="grid gap-4">
        <Skeleton className="h-72 w-48" />
        <Skeleton className="h-14 w-full" />
      </div>
    )
  }
  if (event.isError) return <FormError message={event.error.message} />
  return <AppearanceForm organizationId={organizationId} event={event.data} />
}

function AppearanceForm({
  organizationId,
  event,
}: {
  organizationId: string
  event: EventDetail
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const inputId = useId()
  const [preview, setPreview] = useState<string | null>(null)
  const [suggested, setSuggested] = useState<string | null>(null)
  const [color, setColor] = useState<string>(
    event.accentColor ?? PLATFORM_ACCENT,
  )
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview)
    },
    [preview],
  )

  const flyer = event.flyer
  const options = [
    ...(suggested ? [{ value: suggested, label: "Do flyer" }] : []),
    ...(event.accentColor &&
    event.accentColor !== suggested &&
    event.accentColor !== PLATFORM_ACCENT
      ? [{ value: event.accentColor, label: "Atual" }]
      : []),
    { value: PLATFORM_ACCENT, label: "Verde FESTA" },
    { value: "#ffffff", label: "Branco" },
  ]

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setError(null)
    if (!ACCEPTED.includes(file.type)) {
      setError("Envie a imagem em JPG, PNG ou WebP.")
      return
    }
    if (file.size > MAX_BYTES) {
      setError("A imagem pode ter até 5 MB.")
      return
    }
    setUploading(true)
    try {
      const info = await inspect(file)
      setPreview(URL.createObjectURL(file))
      const upload = await requestUploadUrl(organizationId, event.id, {
        kind: "FLYER",
        contentType: file.type,
        size: file.size,
      })
      await uploadToStorage(upload, file)
      const saved = await setFlyer(organizationId, event.id, {
        key: upload.key,
        width: info.width,
        height: info.height,
      })
      queryClient.setQueryData(["event", organizationId, event.id], saved)
      if (info.accent) {
        const readable = ensureReadableOnBlack(info.accent)
        setSuggested(readable)
        setColor(readable)
      }
    } catch (err) {
      setPreview(null)
      setError(
        err instanceof ApiError
          ? err.message
          : "Não deu pra enviar o flyer. Confira a conexão e tente de novo.",
      )
    } finally {
      setUploading(false)
    }
  }

  const onContinue = async () => {
    setSaving(true)
    setError(null)
    try {
      const saved = await updateEvent(organizationId, event.id, {
        // Verde da plataforma é o padrão: salva vazio para seguir a marca se ela mudar.
        accentColor: color === PLATFORM_ACCENT ? "" : color,
      })
      queryClient.setQueryData(["event", organizationId, event.id], saved)
      router.push(`/painel/eventos/${event.id}/ingressos`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não deu pra salvar.")
      setSaving(false)
    }
  }

  const image = preview ?? flyer?.url ?? null
  const layout = flyer ? coverLayout(flyer.width, flyer.height) : null

  return (
    <div className="grid gap-8">
      <FormError message={error} />

      <section aria-labelledby="flyer-title" className="flex flex-wrap gap-5">
        <div className="relative aspect-[9/16] w-40 shrink-0 overflow-hidden border border-input bg-muted">
          {image ? (
            // eslint-disable-next-line @next/next/no-img-element -- prévia local (blob:) ou do bucket
            <img
              src={image}
              alt="Flyer do evento"
              className="size-full object-cover"
            />
          ) : (
            <span className="flex size-full items-center justify-center p-4 text-center text-sm text-muted-foreground">
              Sem flyer ainda
            </span>
          )}
          {uploading && (
            <span
              role="status"
              className="absolute inset-0 flex items-center justify-center bg-black/70 text-sm font-bold"
            >
              Enviando…
            </span>
          )}
        </div>
        <div className="grid min-w-0 flex-1 content-start gap-3">
          <h2
            id="flyer-title"
            className="font-display text-2xl font-black uppercase"
          >
            Flyer
          </h2>
          <p className="text-sm text-muted-foreground">
            Vira a capa da página e a imagem do link no WhatsApp. JPG, PNG ou
            WebP até 5 MB.
          </p>
          {layout && (
            <p className="text-sm font-bold">{LAYOUT_LABELS[layout]}</p>
          )}
          <label
            htmlFor={inputId}
            className={cn(
              "inline-flex h-11 w-fit cursor-pointer items-center gap-2 border border-input px-4 text-sm font-bold hover:bg-muted",
              uploading && "pointer-events-none opacity-50",
            )}
          >
            <ImageUpIcon className="size-4" aria-hidden />
            {flyer ? "Trocar flyer" : "Escolher flyer"}
          </label>
          <input
            id={inputId}
            type="file"
            accept={ACCEPTED.join(",")}
            className="sr-only"
            disabled={uploading}
            onChange={(e) => {
              void onFile(e.target.files?.[0])
              e.target.value = ""
            }}
          />
        </div>
      </section>

      <section aria-labelledby="cor-title" className="grid gap-3">
        <h2
          id="cor-title"
          className="font-display text-2xl font-black uppercase"
        >
          Cor da página
        </h2>
        <p className="text-sm text-muted-foreground">
          {suggested
            ? "Tirada do flyer e ajustada para ler bem no preto. Dá pra trocar."
            : flyer
              ? "Trocar o flyer sugere uma cor nova a partir dele."
              : "Envie o flyer para sugerirmos a cor dele."}
        </p>
        <div
          role="radiogroup"
          aria-labelledby="cor-title"
          className="flex flex-wrap gap-4"
        >
          {options.map((option) => {
            const on = option.value === color
            return (
              <button
                key={option.value + option.label}
                type="button"
                role="radio"
                aria-checked={on}
                onClick={() => setColor(option.value)}
                className="grid justify-items-center gap-1.5 text-xs font-bold"
              >
                <span
                  aria-hidden
                  className={cn(
                    "size-12 rounded-full transition-shadow",
                    on &&
                      "ring-2 ring-foreground ring-offset-4 ring-offset-background",
                  )}
                  style={{ background: option.value }}
                />
                {option.label}
              </button>
            )
          })}
        </div>
        <div className="mt-2 grid gap-1.5">
          <span className="text-xs font-bold text-muted-foreground">
            Prévia do botão de comprar
          </span>
          <div
            className="flex h-14 items-center justify-between px-5 transition-colors"
            style={{ background: color, color: textOn(color) }}
          >
            <span className="font-display text-2xl font-black uppercase">
              Comprar
            </span>
            <span className="text-sm font-extrabold">a partir de R$ 30,00</span>
          </div>
        </div>
      </section>

      <Button
        type="button"
        size="lg"
        onClick={onContinue}
        disabled={uploading || saving}
        className="h-14 justify-between px-5 font-display text-2xl font-black uppercase"
      >
        {saving ? "Salvando…" : "Continuar"}
        <span aria-hidden>→</span>
      </Button>
    </div>
  )
}
