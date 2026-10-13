import { describe, expect, it } from "vitest"

import {
  newOrderKey,
  parseSelection,
  selectionItems,
  serializeSelection,
  ticketCount,
} from "./selection"

const A = "01a11a25-47a1-7148-b898-a41867f0f624"
const B = "01a11a25-47a1-7148-b898-a41867f0f625"

describe("selection", () => {
  it("vai e volta pelo endereço", () => {
    const selection = { [A]: 2, [B]: 1 }
    expect(parseSelection(serializeSelection(selection))).toEqual(selection)
    expect(ticketCount(selection)).toBe(3)
    expect(selectionItems({ [A]: 2, [B]: 0 })).toEqual([
      { batchId: A, quantity: 2 },
    ])
  })

  it("ignora lixo no endereço", () => {
    expect(parseSelection(null)).toEqual({})
    expect(parseSelection(`x.2,${A}.0,${A}.abc,${B}.99`)).toEqual({})
    expect(parseSelection(`${A}.3,../etc.1`)).toEqual({ [A]: 3 })
  })

  it("gera chave de pedido longa e diferente a cada vez", () => {
    const a = newOrderKey()
    expect(a).toMatch(/^[0-9a-f]{32}$/)
    expect(newOrderKey()).not.toBe(a)
  })
})
