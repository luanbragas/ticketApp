"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { createOrganization } from "@/lib/api/organizations"
import { setCurrentOrganization } from "@/lib/current-org"
import { applyApiError } from "@/lib/forms"
import {
  createOrganizationSchema,
  slugify,
  type CreateOrganizationInput,
} from "@/lib/schemas/organization"

export function CreateOrganizationForm() {
  const router = useRouter()
  const [formError, setFormError] = useState<string | null>(null)
  const [slugEdited, setSlugEdited] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CreateOrganizationInput>({
    resolver: zodResolver(createOrganizationSchema),
    defaultValues: { name: "", slug: "" },
  })
  const slug = useWatch({ control, name: "slug" })

  const nameField = register("name", {
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      if (!slugEdited) setValue("slug", slugify(event.target.value))
    },
  })
  const slugField = register("slug", {
    onChange: () => setSlugEdited(true),
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const organization = await createOrganization(values)
      setCurrentOrganization(organization.id)
      toast.success(`${organization.name} criada.`)
      router.push("/painel")
      router.refresh()
    } catch (error) {
      setFormError(applyApiError(error, setError, ["name", "slug"]))
    }
  })

  return (
    <Card>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="grid gap-5">
          <FormError message={formError} />
          <TextField
            label="Nome"
            placeholder="Atlética de Medicina"
            autoComplete="organization"
            registration={nameField}
            error={errors.name}
          />
          <TextField
            label="Endereço"
            autoCapitalize="none"
            spellCheck={false}
            registration={slugField}
            error={errors.slug}
            hint={
              <>
                Usado no link da sua página:{" "}
                <span className="font-medium break-all text-foreground">
                  festa.com/o/{slug || "…"}
                </span>
              </>
            }
          />
          <Button
            type="submit"
            size="lg"
            className="h-11"
            disabled={isSubmitting}
          >
            {isSubmitting ? "Criando…" : "Criar organização"}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
