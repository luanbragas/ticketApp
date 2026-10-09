"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CopyIcon, PlusIcon } from "lucide-react"
import { useId, useState } from "react"
import { useForm, useWatch, type UseFormRegisterReturn } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { getEvent } from "@/lib/api/events"
import { getTeam } from "@/lib/api/organizations"
import {
  addEventPromoter,
  getEventPromoters,
  listPromoters,
  setPromoterLinkActive,
} from "@/lib/api/promoters"
import type { EventPromoters, PromoterLink } from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import { cn } from "@/lib/utils"

/** Promoters do evento (PLAN.md M7): link para divulgar e vendas pagas atribuídas a cada um. */
export function PromotersView({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const key = ["promoters", organizationId, eventId]
  const promoters = useQuery({
    queryKey: key,
    queryFn: () => getEventPromoters(organizationId, eventId),
  })
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId),
  })

  if (promoters.isPending || event.isPending) {
    return (
      <div aria-busy="true" className="grid gap-3">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }
  if (promoters.isError) return <FormError message={promoters.error.message} />
  if (event.isError) return <FormError message={event.error.message} />

  const data = promoters.data
  const eventName = event.data.name
  const open =
    event.data.status === "DRAFT" || event.data.status === "PUBLISHED"

  return (
    <div className="grid gap-8">
      <div>
        <p className="text-sm text-muted-foreground">{eventName}</p>
        <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
          {data.canManage ? "Promoters" : "Meu link"}
        </h1>
      </div>

      <dl className="grid grid-cols-2 border-y">
        <div className="border-r py-3 pr-3">
          <dt className="text-xs font-bold text-muted-foreground">
            Ingressos vendidos
          </dt>
          <dd className="mt-0.5 font-display text-4xl leading-none font-black">
            {data.tickets}
          </dd>
        </div>
        <div className="py-3 pl-3">
          <dt className="text-xs font-bold text-muted-foreground">Receita</dt>
          <dd className="mt-0.5 font-display text-4xl leading-none font-black">
            {formatCents(data.revenueCents)}
          </dd>
        </div>
      </dl>
      <p className="-mt-6 text-xs text-muted-foreground">
        Só pedidos pagos, sem a taxa de serviço. Vale o último link que o
        comprador abriu nos 7 dias antes da compra.
      </p>

      {data.links.length === 0 ? (
        <p className="text-muted-foreground">
          {data.canManage
            ? "Nenhum promoter nesta festa ainda. Cada um ganha um link próprio para divulgar."
            : "Você ainda não tem link nesta festa. Fale com a organização."}
        </p>
      ) : (
        <ul className="border-t border-foreground">
          {data.links.map((link) => (
            <LinkRow
              key={link.id}
              link={link}
              eventName={eventName}
              canManage={data.canManage}
              organizationId={organizationId}
              eventId={eventId}
            />
          ))}
        </ul>
      )}

      {data.canManage && open && (
        <AddPromoter
          organizationId={organizationId}
          eventId={eventId}
          linked={data}
        />
      )}
    </div>
  )
}

function LinkRow({
  link,
  eventName,
  canManage,
  organizationId,
  eventId,
}: {
  link: PromoterLink
  eventName: string
  canManage: boolean
  organizationId: string
  eventId: string
}) {
  const queryClient = useQueryClient()
  const toggle = useMutation({
    mutationFn: () =>
      setPromoterLinkActive(organizationId, eventId, link.id, !link.active),
    onSuccess: (saved) =>
      queryClient.setQueryData(["promoters", organizationId, eventId], saved),
  })
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link.url)
      toast.success("Link copiado")
    } catch {
      toast.error("Não deu para copiar. Segure o link para copiar.")
    }
  }
  const whatsapp = `https://wa.me/?text=${encodeURIComponent(`Ingressos da ${eventName}: ${link.url}`)}`

  return (
    <li className="grid gap-2 border-b py-4">
      <div className="flex items-baseline justify-between gap-3">
        <p
          className={cn(
            "min-w-0 font-bold",
            !link.active && "text-muted-foreground line-through",
          )}
        >
          {link.name}
          {link.teamMember && (
            <span className="ml-2 text-xs font-extrabold text-muted-foreground uppercase">
              equipe
            </span>
          )}
        </p>
        <p className="shrink-0 text-right text-sm">
          <span className="font-display text-2xl font-black">
            {link.tickets}
          </span>{" "}
          ingr. · {formatCents(link.revenueCents)}
        </p>
      </div>
      <p className="truncate font-mono text-sm text-muted-foreground">
        {link.url.replace(/^https?:\/\//, "")}
      </p>
      {link.phone && (
        <p className="text-sm text-muted-foreground">{link.phone}</p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          className="h-11"
          onClick={copy}
          disabled={!link.active}
        >
          <CopyIcon className="size-4" aria-hidden />
          Copiar link
        </Button>
        <Button asChild variant="outline" className="h-11">
          <a
            href={whatsapp}
            target="_blank"
            rel="noreferrer"
            aria-disabled={!link.active || undefined}
            className={cn(!link.active && "pointer-events-none opacity-50")}
          >
            WhatsApp
          </a>
        </Button>
        {canManage && (
          <Button
            type="button"
            variant="ghost"
            className="h-11 text-muted-foreground"
            disabled={toggle.isPending}
            onClick={() => toggle.mutate()}
          >
            {link.active ? "Desativar" : "Reativar"}
          </Button>
        )}
      </div>
      <FormError message={toggle.error?.message ?? null} />
    </li>
  )
}

const NEW = "novo"

const addSchema = z
  .object({
    promoterId: z.string(),
    name: z.string().trim().max(80, "Nome muito longo."),
    phone: z
      .string()
      .refine(
        (v) => v.trim() === "" || /^[0-9+()\s-]{10,20}$/.test(v),
        "Telefone inválido.",
      ),
    userId: z.string(),
    code: z
      .string()
      .trim()
      .refine(
        (v) => v === "" || /^[a-z0-9]+(-[a-z0-9]+)*$/.test(v),
        "Use letras minúsculas, números e hífens.",
      )
      .refine(
        (v) => v === "" || (v.length >= 3 && v.length <= 30),
        "De 3 a 30 caracteres.",
      ),
  })
  .refine((v) => v.promoterId !== NEW || v.name.length > 0, {
    path: ["name"],
    message: "Informe o nome do promoter.",
  })

type AddInput = z.infer<typeof addSchema>

function AddPromoter({
  organizationId,
  eventId,
  linked,
}: {
  organizationId: string
  eventId: string
  linked: EventPromoters
}) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const known = useQuery({
    queryKey: ["org-promoters", organizationId],
    queryFn: () => listPromoters(organizationId),
    enabled: open,
  })
  const team = useQuery({
    queryKey: ["team", organizationId],
    queryFn: () => getTeam(organizationId),
    enabled: open,
  })
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddInput>({
    resolver: zodResolver(addSchema),
    defaultValues: {
      promoterId: NEW,
      name: "",
      phone: "",
      userId: "",
      code: "",
    },
  })
  const promoterId = useWatch({ control, name: "promoterId" })

  const linkedIds = new Set(linked.links.map((l) => l.promoterId))
  const available = (known.data ?? []).filter((p) => !linkedIds.has(p.id))
  const promoterMembers = (team.data?.members ?? []).filter(
    (m) => m.role === "PROMOTER",
  )

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const saved = await addEventPromoter(
        organizationId,
        eventId,
        values.promoterId !== NEW
          ? { promoterId: values.promoterId, code: values.code || undefined }
          : {
              name: values.name,
              phone: values.phone || undefined,
              userId: values.userId || undefined,
              code: values.code || undefined,
            },
      )
      queryClient.setQueryData(["promoters", organizationId, eventId], saved)
      await queryClient.invalidateQueries({
        queryKey: ["org-promoters", organizationId],
      })
      reset()
      setOpen(false)
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  })

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex h-14 items-center justify-between border border-dashed border-input px-5 font-display text-2xl font-black uppercase hover:border-primary hover:text-primary"
      >
        Adicionar promoter
        <PlusIcon className="size-5" aria-hidden />
      </button>
    )
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="grid gap-4 border border-primary p-4"
    >
      <p className="font-display text-2xl font-black uppercase">
        Novo link de promoter
      </p>
      <FormError message={formError} />
      {available.length > 0 && (
        <SelectField
          label="Promoter"
          registration={register("promoterId")}
          options={[
            { value: NEW, label: "Cadastrar novo" },
            ...available.map((p) => ({ value: p.id, label: p.name })),
          ]}
        />
      )}
      {promoterId === NEW && (
        <>
          <TextField
            label="Nome"
            placeholder="João da Med"
            registration={register("name")}
            error={errors.name}
            autoFocus
          />
          <TextField
            label="WhatsApp (opcional)"
            type="tel"
            placeholder="(31) 99999-0000"
            registration={register("phone")}
            error={errors.phone}
          />
          {promoterMembers.length > 0 && (
            <SelectField
              label="É da equipe? (opcional)"
              hint="Membro com papel Promoter vê as próprias vendas no painel."
              registration={register("userId")}
              options={[
                { value: "", label: "Não" },
                ...promoterMembers.map((m) => ({
                  value: m.userId,
                  label: m.name ?? m.email,
                })),
              ]}
            />
          )}
        </>
      )}
      <TextField
        label="Código do link (opcional)"
        placeholder="joao-da-med"
        hint="Vazio: gerado a partir do nome. Não muda depois."
        registration={register("code")}
        error={errors.code}
      />
      <div className="flex gap-2">
        <Button type="submit" className="h-11 flex-1" disabled={isSubmitting}>
          {isSubmitting ? "Criando…" : "Criar link"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          className="h-11"
          onClick={() => setOpen(false)}
        >
          Cancelar
        </Button>
      </div>
    </form>
  )
}

function SelectField({
  label,
  hint,
  registration,
  options,
}: {
  label: string
  hint?: string
  registration: UseFormRegisterReturn
  options: { value: string; label: string }[]
}) {
  const id = useId()
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        className="h-11 border-b border-input bg-background text-base outline-none focus-visible:border-primary"
        {...registration}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {hint && <p className="text-sm text-muted-foreground">{hint}</p>}
    </div>
  )
}
