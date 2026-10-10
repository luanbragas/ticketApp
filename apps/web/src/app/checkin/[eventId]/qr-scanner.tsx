"use client"

import jsQR from "jsqr"
import { useEffect, useRef, useState } from "react"

/** Leitor nativo do navegador (Chrome Android, Safari recente); quando não existe, usa o jsQR. */
type Detector = {
  detect: (source: CanvasImageSource) => Promise<{ rawValue: string }[]>
}

const SCAN_EVERY_MS = 200
/** O mesmo QR na frente da câmera não conta duas vezes seguidas. */
const SAME_CODE_MS = 3_000

/** Câmera traseira lendo QR sem parar; {@code paused} enquanto o resultado está na tela. */
export function QrScanner({
  onRead,
  paused,
}: {
  onRead: (raw: string) => void
  paused: boolean
}) {
  const video = useRef<HTMLVideoElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const last = useRef<{ code: string; at: number } | null>(null)
  const pausedRef = useRef(paused)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    pausedRef.current = paused
  }, [paused])

  useEffect(() => {
    let stream: MediaStream | null = null
    let timer: ReturnType<typeof setTimeout> | undefined
    let stopped = false
    const BarcodeDetectorCtor = (
      globalThis as unknown as {
        BarcodeDetector?: new (o: { formats: string[] }) => Detector
      }
    ).BarcodeDetector
    const native = BarcodeDetectorCtor
      ? new BarcodeDetectorCtor({ formats: ["qr_code"] })
      : null

    const read = async (): Promise<string | null> => {
      const v = video.current
      if (!v || v.readyState < 2) return null
      if (native) {
        const found = await native.detect(v).catch(() => [])
        return found[0]?.rawValue ?? null
      }
      const c = canvas.current
      if (!c) return null
      c.width = v.videoWidth
      c.height = v.videoHeight
      const ctx = c.getContext("2d", { willReadFrequently: true })
      if (!ctx) return null
      ctx.drawImage(v, 0, 0, c.width, c.height)
      const image = ctx.getImageData(0, 0, c.width, c.height)
      return (
        jsQR(image.data, image.width, image.height, {
          inversionAttempts: "dontInvert",
        })?.data ?? null
      )
    }

    const loop = async () => {
      if (stopped) return
      if (!pausedRef.current) {
        const code = await read()
        const now = Date.now()
        if (
          code &&
          !(last.current?.code === code && now - last.current.at < SAME_CODE_MS)
        ) {
          last.current = { code, at: now }
          onRead(code)
        }
      }
      timer = setTimeout(loop, SCAN_EVERY_MS)
    }

    ;(async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "environment" },
          audio: false,
        })
        if (stopped || !video.current) return
        video.current.srcObject = stream
        await video.current.play()
        loop()
      } catch {
        setError(
          "Libere a câmera para ler os QR Codes. Enquanto isso, use a busca por nome.",
        )
      }
    })()

    return () => {
      stopped = true
      clearTimeout(timer)
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [onRead])

  if (error) {
    return (
      <p role="alert" className="px-5 py-10 text-center text-muted-foreground">
        {error}
      </p>
    )
  }
  return (
    <div className="relative mx-5 my-4 aspect-square overflow-hidden bg-secondary">
      <video ref={video} playsInline muted className="size-full object-cover" />
      <canvas ref={canvas} className="hidden" />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-[15%] border-4 border-primary"
      />
      <p className="absolute inset-x-0 bottom-3 text-center text-sm font-bold drop-shadow">
        Aponte para o QR do ingresso
      </p>
    </div>
  )
}
