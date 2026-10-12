import type { Metadata } from "next"

import { LegalPage, type LegalSection } from "@/components/legal/legal-page"

export const metadata: Metadata = { title: "Termos de uso" }

// Rascunho a partir das regras já implementadas (docs/ARCHITECTURE.md). O texto final vem da validação
// jurídica (PLAN.md M9).
const SECTIONS: LegalSection[] = [
  {
    title: "O que é a plataforma",
    paragraphs: [
      "A FESTA vende ingressos de festas organizadas por atléticas, centros acadêmicos e produtores independentes. Quem organiza a festa é o produtor; a plataforma oferece a página do evento, o pagamento, os ingressos e a portaria.",
      "Ao comprar, você contrata o ingresso com o produtor do evento. A plataforma intermedeia a venda e cobra uma taxa de serviço, mostrada antes do pagamento.",
    ],
  },
  {
    title: "Compra e pagamento",
    paragraphs: [
      "Os ingressos ficam reservados por alguns minutos enquanto você paga. Se o pagamento não chegar nesse prazo, a reserva expira e os ingressos voltam para a venda.",
      "O pagamento é processado pelo Mercado Pago. O valor dos ingressos vai para a conta do produtor e a taxa de serviço, para a plataforma. O pedido só é confirmado depois que o Mercado Pago confirma o pagamento.",
      "Se o pagamento for confirmado depois da reserva expirar e não houver mais ingresso, o valor é devolvido automaticamente.",
    ],
  },
  {
    title: "Ingressos",
    paragraphs: [
      "Cada ingresso é nominal, tem um QR Code único e é enviado para o e-mail de quem comprou. Na entrada, apresente o QR Code e um documento com foto do titular.",
      "Não compartilhe o QR Code: ele vale uma entrada, e quem apresentar primeiro entra.",
      "Meia-entrada exige o documento que dá direito ao benefício, conferido na portaria. O produtor pode recusar a entrada sem o documento.",
      "Eventos para maiores de 18 anos exigem a declaração de idade na compra e documento na entrada.",
    ],
  },
  {
    title: "Cancelamento e reembolso",
    paragraphs: [
      "O reembolso é decidido pelo produtor do evento, que pode reembolsar o pedido inteiro pelo painel. O valor pago, incluindo a taxa de serviço, volta pelo mesmo meio de pagamento, e os ingressos do pedido são cancelados.",
      "Ingresso que já foi usado na entrada não pode ser reembolsado.",
      "Os direitos previstos no Código de Defesa do Consumidor continuam valendo. Em caso de dúvida, fale com o produtor pelos contatos da página do evento.",
    ],
  },
  {
    title: "Conta",
    paragraphs: [
      'Comprador não precisa de senha: o acesso a "Meus ingressos" é feito por um link enviado ao seu e-mail. Ao usar o link pela primeira vez, uma conta é criada com o seu e-mail.',
      "Produtores criam conta com e-mail e senha e respondem pelas informações dos eventos que publicam.",
    ],
  },
]

export default function TermsPage() {
  return (
    <LegalPage
      title="Termos de uso"
      sections={SECTIONS}
      other={{ href: "/privacidade", label: "Política de privacidade" }}
    />
  )
}
