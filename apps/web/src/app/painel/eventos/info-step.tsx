"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useRouter } from "next/navigation"
import { useEffect, useId, useState } from "react"
import { useForm, useWatch, type UseFormRegisterReturn } from "react-hook-form"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Toggle } from "@/components/form/toggle"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { createEvent, getEvent, updateEvent } from "@/lib/api/events"
import type { EventDetail } from "@/lib/api/types"
import { applyApiError } from "@/lib/forms"
import {
  endInstant,
  eventInfoSchema,
  toInstant,
  toLocalParts,
  type EventInfoInput,
} from "@/lib/schemas/event"

const EMPTY: EventInfoInput = {
  name: "",
  date: "",
  startTime: "23:00",
  endTime: "05:00",
  venueName: "",
  address: "",
  city: "",
  description: "",
  adultsOnly: true,
  hasOpenBar: false,
}

function fromEvent(event: EventDetail): EventInfoInput {
  const start = event.startsAt ? toLocalParts(event.startsAt) : null
  const end = event.endsAt ? toLocalParts(event.endsAt) : null
  return {
    name: event.name,
    date: start?.date ?? "",
    startTime: start?.time ?? EMPTY.startTime,
    endTime: end?.time ?? EMPTY.endTime,
    venueName: event.venueName ?? "",
    address: event.address ?? "",
    city: event.city ?? "",
    description: event.description ?? "",
    adultsOnly: event.minAge >= 18,
    hasOpenBar: event.hasOpenBar,
  }
}

/** Passo 1. Sem evento ainda, cria o rascunho no primeiro "Continuar". */
export function InfoStep({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId?: string
}) {
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId!),
    enabled: !!eventId,
  })

  if (eventId && event.isPending) return <FormSkeleton />
  if (eventId && event.isError) {
    return <FormError message={event.error.message} />
  }
  return (
    <InfoForm
      organizationId={organizationId}
      event={event.data}
      key={event.data?.id ?? "novo"}
    />
  )
}

function InfoForm({
  organizationId,
  event,
}: {
  organizationId: string
  event?: EventDetail
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<EventInfoInput>({
    resolver: zodResolver(eventInfoSchema),
    defaultValues: event ? fromEvent(event) : EMPTY,
  })
  const hasOpenBar = useWatch({ control, name: "hasOpenBar" })

  // Open bar liga o 18+ junto (regra do banco e da API).
  useEffect(() => {
    if (hasOpenBar) setValue("adultsOnly", true)
  }, [hasOpenBar, setValue])

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const id =
        event?.id ?? (await createEvent(organizationId, values.name)).id
      const saved = await updateEvent(organizationId, id, {
        name: values.name,
        startsAt: toInstant(values.date, values.startTime),
        endsAt: endInstant(values.date, values.startTime, values.endTime),
        venueName: values.venueName,
        address: values.address,
        city: values.city,
        description: values.description,
        minAge: values.adultsOnly ? 18 : 0,
        hasOpenBar: values.hasOpenBar,
      })
      queryClient.setQueryData(["event", organizationId, id], saved)
      router.push(`/painel/eventos/${id}/aparencia`)
    } catch (error) {
      setFormError(
        applyApiError(error, setError, [
          "name",
          "venueName",
          "address",
          "city",
          "description",
        ]),
      )
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-6">
      <FormError message={formError} />
      <TextField
        label="Nome da festa"
        placeholder="Calourada Med 26"
        registration={register("name")}
        error={errors.name}
      />
      <div className="grid gap-1.5">
        <div className="grid grid-cols-[1.4fr_1fr_1fr] gap-3">
          <TextField
            label="Data"
            type="date"
            registration={register("date")}
            error={errors.date}
          />
          <TextField
            label="Abre"
            type="time"
            registration={register("startTime")}
            error={errors.startTime}
          />
          <TextField
            label="Fecha"
            type="time"
            registration={register("endTime")}
            error={errors.endTime}
          />
        </div>
        <p className="text-sm text-muted-foreground">
          Fechamento antes da abertura conta como dia seguinte. Horário de
          Brasília.
        </p>
      </div>
      <TextField
        label="Local"
        placeholder="Galpão 42"
        registration={register("venueName")}
        error={errors.venueName}
      />
      <div className="grid gap-3 sm:grid-cols-[1.6fr_1fr]">
        <TextField
          label="Endereço"
          placeholder="Av. dos Andradas, 4200"
          autoComplete="street-address"
          registration={register("address")}
          error={errors.address}
        />
        <TextField
          label="Cidade"
          placeholder="Belo Horizonte"
          autoComplete="address-level2"
          registration={register("city")}
          error={errors.city}
        />
      </div>
      <TextArea
        label="Sobre a festa"
        placeholder="Open bar até 3h, bateria ao vivo e DJ convidado."
        registration={register("description")}
        error={errors.description?.message}
      />
      <fieldset className="grid border-t">
        <legend className="sr-only">Regras de entrada</legend>
        <Toggle label="Tem open bar" registration={register("hasOpenBar")} />
        <Toggle
          label="Só para maiores de 18"
          hint="Documento com foto na entrada."
          registration={register("adultsOnly")}
          disabled={hasOpenBar}
          error={errors.adultsOnly?.message}
        />
      </fieldset>
      <Button
        type="submit"
        size="lg"
        className="h-14 justify-between px-5 font-display text-2xl font-black uppercase"
        disabled={isSubmitting}
      >
        {isSubmitting ? "Salvando…" : "Continuar"}
        <span aria-hidden>→</span>
      </Button>
    </form>
  )
}

function TextArea({
  label,
  placeholder,
  registration,
  error,
}: {
  label: string
  placeholder?: string
  registration: UseFormRegisterReturn
  error?: string
}) {
  const id = useId()
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <textarea
        id={id}
        rows={3}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="min-h-24 resize-y border-b border-input bg-transparent py-2 text-base outline-none focus-visible:border-primary"
        {...registration}
      />
      {error && (
        <p id={`${id}-error`} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}

function FormSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-6">
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-28 w-full" />
    </div>
  )
}
