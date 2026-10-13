"use client"

import { useId } from "react"
import type { UseFormRegisterReturn } from "react-hook-form"

/** Linha com rótulo e interruptor (checkbox com role="switch"), em lista separada por linhas. */
export function Toggle({
  label,
  hint,
  registration,
  disabled,
  error,
}: {
  label: string
  hint?: string
  registration: UseFormRegisterReturn
  disabled?: boolean
  error?: string
}) {
  const id = useId()
  return (
    <div className="border-b py-3">
      <label
        htmlFor={id}
        className="flex min-h-11 cursor-pointer items-center gap-3"
      >
        <span className="flex-1">
          <span className="block font-bold">{label}</span>
          {hint && (
            <span className="block text-sm text-muted-foreground">{hint}</span>
          )}
        </span>
        <input
          id={id}
          type="checkbox"
          role="switch"
          disabled={disabled}
          className="size-6 accent-primary disabled:opacity-60"
          {...registration}
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
