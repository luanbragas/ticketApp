"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useRouter } from "next/navigation"
import { useId, useState } from "react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Button } from "@/components/ui/button"
import { uploadToStorage } from "@/lib/api/events"
import {
  requestLogoUploadUrl,
  setOrganizationLogo,
  updateOrganization,
} from "@/lib/api/organizations"
import type { Organization } from "@/lib/api/types"

const ACCEPTED = ["image/jpeg", "image/png", "image/webp"]
const MAX_BYTES = 5 * 1024 * 1024

const schema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Informe o nome.")
    .max(80, "Nome muito longo."),
  instagram: z
    .string()
    .trim()
    .max(100, "Instagram muito longo.")
    .refine(
      (v) =>
        v === "" ||
        /^(https?:\/\/)?(www\.)?(instagram\.com\/)?@?[A-Za-z0-9._]{1,30}\/?$/.test(
          v,
        ),
      "Use o @ do perfil.",
    ),
  whatsapp: z
    .string()
    .refine(
      (v) => v.trim() === "" || /^[0-9+()\s-]{10,20}$/.test(v),
      "Use DDD e número.",
    ),
})

type Input = z.infer<typeof schema>

/** Nome, Instagram, WhatsApp e logo: o que o público vê de quem produz a festa. */
export function OrganizationProfile({
  organization,
}: {
  organization: Organization
}) {
  const router = useRouter()
  const fileId = useId()
  const [formError, setFormError] = useState<string | null>(null)
  const [logoUrl, setLogoUrl] = useState(organization.logoUrl)
  const [uploading, setUploading] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<Input>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: organization.name,
      instagram: organization.instagram ? `@${organization.instagram}` : "",
      whatsapp: organization.whatsapp ?? "",
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const saved = await updateOrganization(organization.id, values)
      reset({
        name: saved.name,
        instagram: saved.instagram ? `@${saved.instagram}` : "",
        whatsapp: saved.whatsapp ?? "",
      })
      toast.success("Dados salvos")
      router.refresh()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  })

  const onLogo = async (file: File | undefined) => {
    if (!file) return
    setFormError(null)
    if (!ACCEPTED.includes(file.type)) {
      setFormError("Envie a logo em JPG, PNG ou WebP.")
      return
    }
    if (file.size > MAX_BYTES) {
      setFormError("A logo pode ter até 5 MB.")
      return
    }
    setUploading(true)
    try {
      const upload = await requestLogoUploadUrl(organization.id, {
        contentType: file.type,
        size: file.size,
      })
      await uploadToStorage(upload, file)
      const saved = await setOrganizationLogo(organization.id, upload.key)
      setLogoUrl(saved.logoUrl)
      toast.success("Logo atualizada")
      router.refresh()
    } catch (error) {
      setFormError(
        error instanceof Error ? error.message : "Não deu para enviar a logo.",
      )
    } finally {
      setUploading(false)
    }
  }

  const removeLogo = async () => {
    setFormError(null)
    try {
      await setOrganizationLogo(organization.id, null)
      setLogoUrl(null)
      router.refresh()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  }

  return (
    <div className="grid gap-8">
      <FormError message={formError} />

      <section aria-labelledby="logo" className="flex items-center gap-4">
        <div className="flex size-20 shrink-0 items-center justify-center overflow-hidden bg-secondary">
          {logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- imagem do bucket da organização
            <img
              src={logoUrl}
              alt="Logo da organização"
              className="size-full object-cover"
            />
          ) : (
            <span className="font-display text-3xl font-black">
              {organization.name.charAt(0).toUpperCase()}
            </span>
          )}
        </div>
        <div className="grid gap-2">
          <p id="logo" className="font-bold">
            Logo
          </p>
          <div className="flex flex-wrap gap-2">
            <label
              htmlFor={fileId}
              className="flex h-10 cursor-pointer items-center border border-input px-4 text-sm font-bold hover:border-primary"
            >
              {uploading ? "Enviando…" : logoUrl ? "Trocar" : "Enviar logo"}
            </label>
            <input
              id={fileId}
              type="file"
              accept={ACCEPTED.join(",")}
              className="sr-only"
              disabled={uploading}
              onChange={(e) => onLogo(e.target.files?.[0])}
            />
            {logoUrl && (
              <Button
                type="button"
                variant="ghost"
                className="h-10"
                onClick={removeLogo}
              >
                Tirar
              </Button>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            Quadrada, JPG, PNG ou WebP, até 5 MB.
          </p>
        </div>
      </section>

      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <TextField
          label="Nome"
          registration={register("name")}
          error={errors.name}
        />
        <TextField
          label="Instagram"
          placeholder="@atletica"
          registration={register("instagram")}
          error={errors.instagram}
        />
        <TextField
          label="WhatsApp de contato"
          type="tel"
          placeholder="(31) 99999-0000"
          registration={register("whatsapp")}
          error={errors.whatsapp}
          hint="Para quem comprou falar com a organização."
        />
        <Button
          type="submit"
          className="h-11 w-fit"
          disabled={isSubmitting || !isDirty}
        >
          {isSubmitting ? "Salvando…" : "Salvar"}
        </Button>
      </form>
    </div>
  )
}
