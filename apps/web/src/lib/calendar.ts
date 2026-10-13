/** "Adicionar à agenda": link do Google Agenda e arquivo .ics (Apple, Outlook). */

type CalendarEvent = {
  title: string
  startsAt: string
  endsAt: string
  location: string
  details: string
}

/** "2026-11-15T02:00:00Z" → "20261115T020000Z" (formato das agendas). */
function stamp(iso: string): string {
  return new Date(iso)
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}/, "")
}

export function googleCalendarUrl(event: CalendarEvent): string {
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: event.title,
    dates: `${stamp(event.startsAt)}/${stamp(event.endsAt)}`,
    location: event.location,
    details: event.details,
  })
  return `https://calendar.google.com/calendar/render?${params}`
}

/** Escapa texto do .ics (RFC 5545): barra, ponto e vírgula, vírgula e quebra de linha. */
function escapeIcs(text: string): string {
  return text
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n")
}

export function icsContent(event: CalendarEvent, uid: string): string {
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//FESTA//Ingresso//PT",
    "BEGIN:VEVENT",
    `UID:${uid}@festa`,
    `DTSTAMP:${stamp(event.startsAt)}`,
    `DTSTART:${stamp(event.startsAt)}`,
    `DTEND:${stamp(event.endsAt)}`,
    `SUMMARY:${escapeIcs(event.title)}`,
    `LOCATION:${escapeIcs(event.location)}`,
    `DESCRIPTION:${escapeIcs(event.details)}`,
    "END:VEVENT",
    "END:VCALENDAR",
  ].join("\r\n")
}
