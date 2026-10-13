"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useQuery } from "@tanstack/react-query"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { useId, useMemo, useState } from "react"
import {
  useFieldArray,
  useForm,
  type FieldErrors,
  type Path,
  type UseFormRegisterReturn,
} from "react-hook-form"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { ApiError } from "@/lib/api/client"
import { placeOrder, quote } from "@/lib/api/orders"
import { HALF_PRICE_REASONS, type Quote } from "@/lib/api/types"
import { formatCpf } from "@/lib/cpf"
import { formatCents } from "@/lib/money"
import {
  checkoutSchema,
  formPath,
  toOrderBody,
  type CheckoutInput,
} from "@/lib/schemas/checkout"
import {
  newOrderKey,
  parseSelection,
  selectionItems,
  serializeSelection,
} from "@/lib/selection"
import { rememberOrderKey } from "@/lib/order-keys"

/** Checkout em uma coluna (PLAN.md M4): comprador, titulares, meia, 18+, termos e resumo. */
export function CheckoutForm({
  slug,
  adultsOnly,
}: {
  slug: string
  adultsOnly: boolean
}) {
  const params = useSearchParams()
  const selection = parseSelection(params.get("i"))
  const items = selectionItems(selection)
  const priced = useQuery({
    queryKey: ["quote", slug, serializeSelection(selection)],
    queryFn: () => quote(slug, items),
    enabled: items.length > 0,
    retry: false,
  })

  if (items.length === 0) {
    return <Problem slug={slug} message="Escolha os ingressos primeiro." />
  }
  if (priced.isPending) {
    return (
      <div aria-busy="true" className="mt-8 grid gap-4">
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }
  if (priced.isError) {
    return <Problem slug={slug} message={priced.error.message} />
  }
  return <Form slug={slug} adultsOnly={adultsOnly} quote={priced.data} />
}

function Problem({ slug, message }: { slug: string; message: string }) {
  return (
    <div className="mt-8 grid gap-4">
      <FormError message={message} />
      <Link
        href={`/e/${slug}#ingressos`}
        className="flex h-14 items-center justify-between bg-(--accent) px-5 font-display text-2xl font-black text-(--on-accent) uppercase"
      >
        Escolher ingressos
        <span aria-hidden>←</span>
      </Link>
    </div>
  )
}

function Form({
  slug,
  adultsOnly,
  quote,
}: {
  slug: string
  adultsOnly: boolean
  quote: Quote
}) {
  const router = useRouter()
  // Uma chave por tela: clicar duas vezes ou repetir depois de erro de rede não cria outro pedido.
  const [orderKey] = useState(newOrderKey)
  const [formError, setFormError] = useState<string | null>(null)
  const [soldOut, setSoldOut] = useState(false)
  const schema = useMemo(() => checkoutSchema(adultsOnly), [adultsOnly])
  const tickets = useMemo(
    () =>
      quote.lines.flatMap((line) =>
        Array.from({ length: line.quantity }, () => ({
          batchId: line.batchId,
          label: `${line.typeName} · ${line.batchName}`,
          halfPrice: line.halfPrice,
        })),
      ),
    [quote],
  )
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    getValues,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CheckoutInput>({
    resolver: zodResolver(schema),
    defaultValues: {
      buyer: { name: "", email: "", phone: "", cpf: "" },
      tickets: tickets.map((t) => ({
        batchId: t.batchId,
        halfPrice: t.halfPrice,
        holderName: "",
        holderCpf: "",
        halfPriceReason: "",
      })),
      adult: false,
      terms: false as unknown as true,
    },
  })
  const { fields } = useFieldArray({ control, name: "tickets" })

  const cpfField = (name: Path<CheckoutInput>): UseFormRegisterReturn => {
    const registration = register(name)
    return {
      ...registration,
      onChange: (event) => {
        const input = event.target as HTMLInputElement
        input.value = formatCpf(input.value)
        return registration.onChange(event)
      },
    }
  }

  const fillWithBuyer = (index: number) => {
    setValue(`tickets.${index}.holderName`, getValues("buyer.name"), {
      shouldValidate: true,
    })
    setValue(`tickets.${index}.holderCpf`, getValues("buyer.cpf"), {
      shouldValidate: true,
    })
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    setSoldOut(false)
    try {
      const order = await placeOrder(orderKey, toOrderBody(slug, values))
      rememberOrderKey(order.id, orderKey)
      router.push(`/pedido/${order.id}`)
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setFormError("Algo deu errado. Tente de novo.")
        return
      }
      if (error.code === "batch-unavailable" || error.code === "sales-closed") {
        setSoldOut(true)
      }
      let matched = false
      for (const fieldError of error.errors) {
        setError(formPath(fieldError.field) as Path<CheckoutInput>, {
          message: fieldError.message,
        })
        matched = true
      }
      if (typeof error.extra.field === "string") {
        setError(formPath(error.extra.field) as Path<CheckoutInput>, {
          message: error.message,
        })
        matched = true
      }
      if (!matched) setFormError(error.message)
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="mt-8 grid gap-10">
      <section aria-labelledby="comprador" className="grid gap-4">
        <SectionTitle id="comprador">Seus dados</SectionTitle>
        <TextField
          label="Nome completo"
          autoComplete="name"
          registration={register("buyer.name")}
          error={errors.buyer?.name}
        />
        <TextField
          label="E-mail"
          type="email"
          autoComplete="email"
          hint="Os ingressos chegam aqui."
          registration={register("buyer.email")}
          error={errors.buyer?.email}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="CPF"
            inputMode="numeric"
            placeholder="000.000.000-00"
            registration={cpfField("buyer.cpf")}
            error={errors.buyer?.cpf}
          />
          <TextField
            label="Celular (opcional)"
            type="tel"
            autoComplete="tel"
            placeholder="(31) 99999-0000"
            registration={register("buyer.phone")}
            error={errors.buyer?.phone}
          />
        </div>
      </section>

      <section aria-labelledby="titulares" className="grid gap-6">
        <SectionTitle id="titulares">
          {fields.length === 1 ? "Titular" : "Titulares"}
        </SectionTitle>
        <p className="-mt-3 text-sm text-muted-foreground">
          Cada ingresso sai no nome de quem vai entrar. Documento com foto na
          porta.
        </p>
        {fields.map((field, index) => (
          <fieldset key={field.id} className="grid gap-3 border-t pt-4">
            <legend className="sr-only">Ingresso {index + 1}</legend>
            <div className="flex items-baseline justify-between gap-3">
              <p className="font-bold">
                {index + 1}. {tickets[index].label}
              </p>
              <button
                type="button"
                onClick={() => fillWithBuyer(index)}
                className="min-h-11 shrink-0 text-sm font-bold text-(--accent)"
              >
                Sou eu
              </button>
            </div>
            <div className="grid grid-cols-[1.4fr_1fr] gap-3">
              <TextField
                label="Nome do titular"
                registration={register(`tickets.${index}.holderName`)}
                error={errors.tickets?.[index]?.holderName}
              />
              <TextField
                label="CPF"
                inputMode="numeric"
                placeholder="000.000.000-00"
                registration={cpfField(`tickets.${index}.holderCpf`)}
                error={errors.tickets?.[index]?.holderCpf}
              />
            </div>
            {tickets[index].halfPrice && (
              <Select
                label="Benefício da meia-entrada"
                hint="Leve o documento: sem ele, paga a diferença na porta."
                registration={register(`tickets.${index}.halfPriceReason`)}
                error={errors.tickets?.[index]?.halfPriceReason?.message}
              />
            )}
          </fieldset>
        ))}
      </section>

      <Summary quote={quote} />

      <div className="grid gap-1 border-y">
        {adultsOnly && (
          <Check
            label="Todos os titulares têm 18 anos ou mais"
            registration={register("adult")}
            error={errors.adult?.message}
          />
        )}
        <Check
          label="Li e aceito os termos de uso e a política de privacidade"
          registration={register("terms")}
          error={(errors as FieldErrors<{ terms: boolean }>).terms?.message}
        />
      </div>

      <FormError message={formError} />
      {soldOut && (
        <Link
          href={`/e/${slug}#ingressos`}
          className="-mt-6 text-sm font-bold underline underline-offset-4"
        >
          Escolher outros ingressos
        </Link>
      )}

      <button
        type="submit"
        disabled={isSubmitting}
        className="flex h-14 items-center justify-between bg-(--accent) px-5 text-(--on-accent) disabled:opacity-60"
      >
        <span className="font-display text-2xl font-black uppercase">
          {isSubmitting ? "Reservando…" : "Reservar"}
        </span>
        <span className="font-extrabold">{formatCents(quote.totalCents)}</span>
      </button>
      <p className="-mt-8 text-center text-xs text-muted-foreground">
        Os ingressos ficam guardados por 10 minutos enquanto você paga.
      </p>
    </form>
  )
}

function SectionTitle({
  id,
  children,
}: {
  id: string
  children: React.ReactNode
}) {
  return (
    <h2
      id={id}
      className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
    >
      {children}
    </h2>
  )
}

function Summary({ quote }: { quote: Quote }) {
  return (
    <section aria-labelledby="resumo" className="grid gap-2">
      <SectionTitle id="resumo">Resumo</SectionTitle>
      <dl className="grid gap-1.5 text-sm">
        {quote.lines.map((line) => (
          <div key={line.batchId} className="flex justify-between gap-3">
            <dt>
              {line.quantity}× {line.typeName} · {line.batchName}
            </dt>
            <dd>{formatCents(line.unitPriceCents * line.quantity)}</dd>
          </div>
        ))}
        <div className="flex justify-between gap-3 text-muted-foreground">
          <dt>Taxa de serviço</dt>
          <dd>{formatCents(quote.feeCents)}</dd>
        </div>
        <div className="mt-1 flex items-baseline justify-between gap-3 border-t pt-2">
          <dt className="font-bold">Total</dt>
          <dd className="font-display text-3xl font-black">
            {formatCents(quote.totalCents)}
          </dd>
        </div>
      </dl>
    </section>
  )
}

function Select({
  label,
  hint,
  registration,
  error,
}: {
  label: string
  hint: string
  registration: UseFormRegisterReturn
  error?: string
}) {
  const id = useId()
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-help`}
        className="h-11 border-b border-input bg-background text-base outline-none focus-visible:border-(--accent)"
        {...registration}
      >
        <option value="">Escolha…</option>
        {Object.entries(HALF_PRICE_REASONS).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
      <p
        id={`${id}-help`}
        role={error ? "alert" : undefined}
        className={
          error ? "text-sm text-destructive" : "text-sm text-muted-foreground"
        }
      >
        {error ?? hint}
      </p>
    </div>
  )
}

function Check({
  label,
  registration,
  error,
}: {
  label: string
  registration: UseFormRegisterReturn
  error?: string
}) {
  const id = useId()
  return (
    <div className="py-2">
      <label
        htmlFor={id}
        className="flex min-h-11 cursor-pointer items-center gap-3"
      >
        <input
          id={id}
          type="checkbox"
          className="size-5 shrink-0 accent-(--accent)"
          aria-invalid={error ? true : undefined}
          {...registration}
        />
        <span className="text-sm">{label}</span>
      </label>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
