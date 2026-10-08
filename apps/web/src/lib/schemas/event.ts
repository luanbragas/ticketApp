import { z } from "zod"

/**
 * Datas do formulário são de parede em São Paulo (sem horário de verão desde 2019, sempre -03:00).
 * A API recebe e devolve instantes ISO-8601 (CLAUDE.md regra 2).
 */
const SAO_PAULO_OFFSET = "-03:00"
const TIME_ZONE = "America/Sao_Paulo"

/** "2026-11-14" + "23:00" → "2026-11-14T23:00:00-03:00". */
export function toInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00${SAO_PAULO_OFFSET}`).toISOString()
}

/** Fim no mesmo dia ou, se o horário for menor ou igual ao início, no dia seguinte (festa vira a noite). */
export function endInstant(date: string, start: string, end: string): string {
  const startsAt = new Date(toInstant(date, start))
  const endsAt = new Date(toInstant(date, end))
  if (endsAt <= startsAt) endsAt.setUTCDate(endsAt.getUTCDate() + 1)
  return endsAt.toISOString()
}

/** Instante da API → data e hora de São Paulo para os campos do formulário. */
export function toLocalParts(iso: string): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ""
  return {
    date: `${get("year")}-${get("month")}-${get("day")}`,
    time: `${get("hour")}:${get("minute")}`,
  }
}

const time = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Use o formato 23:00.")

export const eventInfoSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "Informe o nome da festa.")
      .max(120, "Nome muito longo."),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Informe a data."),
    startTime: time,
    endTime: time,
    venueName: z
      .string()
      .trim()
      .min(1, "Informe o local.")
      .max(120, "Nome do local muito longo."),
    address: z.string().trim().max(300, "Endereço muito longo."),
    city: z.string().trim().max(80, "Cidade muito longa."),
    description: z.string().trim().max(5000, "Descrição muito longa."),
    adultsOnly: z.boolean(),
    hasOpenBar: z.boolean(),
  })
  .refine((v) => !v.hasOpenBar || v.adultsOnly, {
    path: ["adultsOnly"],
    message: "Com open bar, a festa precisa ser só para maiores de 18.",
  })

export type EventInfoInput = z.infer<typeof eventInfoSchema>
