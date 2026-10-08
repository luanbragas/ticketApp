// Comportamentos comuns dos kits: colagem reativa, brilho holográfico, contador de câmera,
// seleção de ingressos e pausa de animações fora da tela. Sem dependências.

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches

/* Colagem reativa: camadas com data-depth se deslocam com o dedo, o mouse ou a inclinação. */
function setupParallax(root) {
  if (reduceMotion) return
  const apply = (x, y) => {
    root.style.setProperty("--px", x.toFixed(3))
    root.style.setProperty("--py", y.toFixed(3))
    // Brilho holográfico usa o mesmo sinal, em graus.
    root.style.setProperty("--tilt-x", `${(x * 12).toFixed(1)}deg`)
    root.style.setProperty("--tilt-y", `${(y * 12).toFixed(1)}deg`)
    root.style.setProperty("--shine", `${(50 + x * 40).toFixed(1)}%`)
  }
  root.addEventListener("pointermove", (event) => {
    const box = root.getBoundingClientRect()
    apply(((event.clientX - box.left) / box.width - 0.5) * 2, ((event.clientY - box.top) / box.height - 0.5) * 2)
  })
  root.addEventListener("pointerleave", () => apply(0, 0))
  window.addEventListener("deviceorientation", (event) => {
    if (event.gamma == null || event.beta == null) return
    apply(Math.max(-1, Math.min(1, event.gamma / 30)), Math.max(-1, Math.min(1, (event.beta - 45) / 30)))
  })
}

/* Contador de câmera (REC): 00:00:00:00 rodando. */
function setupTimecode(el) {
  const start = performance.now()
  const pad = (n) => String(n).padStart(2, "0")
  const tick = () => {
    const ms = performance.now() - start
    const frames = Math.floor((ms % 1000) / (1000 / 30))
    const s = Math.floor(ms / 1000)
    el.textContent = `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}:${pad(frames)}`
    if (!reduceMotion) requestAnimationFrame(tick)
  }
  tick()
}

/* Ingressos: [data-tier] com preço em centavos; [data-total] mostra o total com 10% de taxa. */
function setupCheckout(form) {
  const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
  const render = () => {
    let count = 0
    let subtotal = 0
    form.querySelectorAll("[data-tier]").forEach((tier) => {
      const qty = Number(tier.dataset.qty || 0)
      const price = Number(tier.dataset.price)
      tier.querySelector("[data-qty-out]").textContent = qty
      tier.classList.toggle("is-on", qty > 0)
      const minus = tier.querySelector("[data-minus]")
      if (minus) minus.disabled = qty === 0
      count += qty
      subtotal += qty * price
    })
    const total = subtotal + Math.round(subtotal * 0.1)
    form.querySelectorAll("[data-total]").forEach((el) => (el.textContent = brl.format(total / 100)))
    form.querySelectorAll("[data-count]").forEach((el) => (el.textContent = count === 1 ? "1 ingresso" : `${count} ingressos`))
    form.querySelectorAll("[data-pay]").forEach((el) => {
      el.toggleAttribute("aria-disabled", count === 0)
      el.classList.toggle("is-empty", count === 0)
    })
    form.dispatchEvent(new CustomEvent("checkout:change", { detail: { count, total } }))
  }
  form.addEventListener("click", (event) => {
    const button = event.target.closest("[data-plus], [data-minus]")
    if (!button) return
    const tier = button.closest("[data-tier]")
    const qty = Number(tier.dataset.qty || 0) + (button.hasAttribute("data-plus") ? 1 : -1)
    tier.dataset.qty = String(Math.max(0, Math.min(6, qty)))
    tier.classList.remove("bump")
    void tier.offsetWidth
    tier.classList.add("bump")
    render()
  })
  form.querySelectorAll("[data-choice] button").forEach((button) => {
    button.addEventListener("click", () => {
      button.parentElement.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b === button)))
    })
  })
  render()
}

/* Pausa loops contínuos de molduras fora da tela (bateria). */
function setupPause(elements) {
  const io = new IntersectionObserver((entries) => {
    entries.forEach((entry) => entry.target.classList.toggle("is-paused", !entry.isIntersecting))
  })
  elements.forEach((el) => io.observe(el))
}

document.querySelectorAll(".phone, .art").forEach(setupParallax)
document.querySelectorAll("[data-timecode]").forEach(setupTimecode)
document.querySelectorAll("[data-checkout]").forEach(setupCheckout)
setupPause(document.querySelectorAll(".phone, .art"))
