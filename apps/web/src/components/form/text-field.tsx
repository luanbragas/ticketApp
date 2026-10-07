"use client"

import { useId } from "react"
import type { FieldError, UseFormRegisterReturn } from "react-hook-form"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

type TextFieldProps = {
  label: string
  registration: UseFormRegisterReturn
  error?: FieldError
  hint?: React.ReactNode
} & Omit<React.ComponentProps<typeof Input>, "id" | keyof UseFormRegisterReturn>

/** Campo com label, dica e erro ligados por aria (FRONTEND.md §Acessibilidade). */
export function TextField({
  label,
  registration,
  error,
  hint,
  className,
  ...inputProps
}: TextFieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined

  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className="h-11"
        aria-invalid={error ? true : undefined}
        aria-describedby={
          [hintId, errorId].filter(Boolean).join(" ") || undefined
        }
        {...inputProps}
        {...registration}
      />
      {hint && !error && (
        <p id={hintId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error.message}
        </p>
      )}
    </div>
  )
}
