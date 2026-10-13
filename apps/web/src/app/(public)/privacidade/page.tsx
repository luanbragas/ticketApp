import type { Metadata } from "next"

import { LegalPage, type LegalSection } from "@/components/legal/legal-page"

export const metadata: Metadata = { title: "Política de privacidade" }

// Rascunho a partir de docs/SECURITY.md §LGPD. O texto final vem da validação jurídica (PLAN.md M9).
const SECTIONS: LegalSection[] = [
  {
    title: "Quem trata os seus dados",
    paragraphs: [
      "A FESTA é controladora dos dados da sua conta (nome, e-mail, senha cifrada) e dos registros de acesso e de aceite destes termos.",
      "Para os dados dos participantes de um evento (comprador e titulares), a plataforma trata os dados em nome do produtor do evento, que decide como usá-los na organização da festa.",
    ],
  },
  {
    title: "Quais dados e para quê",
    paragraphs: [
      "Na compra: nome, e-mail, telefone (opcional) e CPF de quem compra, e nome e CPF de cada titular. Servem para emitir o ingresso nominal, aplicar o limite de ingressos por CPF definido pelo produtor e conferir a entrada.",
      "No aceite destes termos: a versão aceita, data, endereço IP e navegador, como prova do aceite.",
      "Na entrada do evento: data e hora do check-in de cada ingresso.",
      "A base legal é a execução do contrato de compra. Comunicações de marketing só com consentimento separado.",
    ],
  },
  {
    title: "Como protegemos",
    paragraphs: [
      "O CPF é guardado cifrado e aparece mascarado nas telas, inclusive para o produtor. O QR Code do ingresso não contém dados pessoais.",
      "Os dados de pagamento com cartão são digitados no ambiente do Mercado Pago e não passam pelos servidores da plataforma.",
      "O acesso ao painel é restrito à equipe de cada organização, com papéis e registro das ações sensíveis.",
    ],
  },
  {
    title: "Com quem compartilhamos",
    paragraphs: [
      "Com o produtor do evento que você comprou; com o Mercado Pago, para processar o pagamento; com o provedor de e-mail, para enviar os ingressos; e com provedores de hospedagem e monitoramento de erros, sob contrato.",
    ],
  },
  {
    title: "Seus direitos",
    paragraphs: [
      "Você pode pedir acesso, correção ou exclusão dos seus dados e informações sobre o tratamento, nos termos da LGPD. Dados de pedidos e pagamentos são guardados pelo prazo exigido pela legislação fiscal e de defesa do consumidor.",
    ],
  },
]

export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacidade"
      sections={SECTIONS}
      other={{ href: "/termos", label: "Termos de uso" }}
    />
  )
}
