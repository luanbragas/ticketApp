"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { MailIcon, RotateCwIcon } from "lucide-react"
import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { getTeam, inviteMember } from "@/lib/api/organizations"
import { ROLE_LABELS, type Role } from "@/lib/api/types"
import { applyApiError } from "@/lib/forms"
import {
  inviteSchema,
  invitableRoles,
  type InviteInput,
} from "@/lib/schemas/organization"

const dateFormat = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "short",
  timeZone: "America/Sao_Paulo",
})

export function TeamView({
  organizationId,
  myRole,
}: {
  organizationId: string
  myRole: Role
}) {
  const team = useQuery({
    queryKey: ["team", organizationId],
    queryFn: () => getTeam(organizationId),
  })
  const roles = invitableRoles(myRole)

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-start">
      <section aria-labelledby="membros" className="grid gap-3">
        <h2 id="membros" className="text-sm font-medium text-muted-foreground">
          Membros
        </h2>

        {team.isPending && (
          <Card>
            <CardContent className="grid gap-4">
              {[0, 1, 2].map((i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="size-9 rounded-full" />
                  <div className="grid flex-1 gap-1.5">
                    <Skeleton className="h-4 w-40" />
                    <Skeleton className="h-3 w-56" />
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        )}

        {team.isError && (
          <Card>
            <CardContent className="flex flex-col items-start gap-3 text-sm">
              <p>{team.error.message}</p>
              <Button variant="outline" onClick={() => team.refetch()}>
                <RotateCwIcon className="size-4" aria-hidden />
                Tentar de novo
              </Button>
            </CardContent>
          </Card>
        )}

        {team.data && (
          <Card className="py-0">
            <ul className="divide-y">
              {team.data.members.map((member) => (
                <li
                  key={member.userId}
                  className="flex items-center gap-3 px-4 py-3"
                >
                  <span
                    aria-hidden
                    className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold"
                  >
                    {(member.name ?? member.email).charAt(0).toUpperCase()}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {member.name ?? "Sem nome"}
                    </p>
                    <p className="truncate text-sm text-muted-foreground">
                      {member.email}
                    </p>
                  </div>
                  <Badge
                    variant={member.role === "OWNER" ? "default" : "secondary"}
                  >
                    {ROLE_LABELS[member.role]}
                  </Badge>
                </li>
              ))}
              {team.data.pendingInvitations.map((invitation) => (
                <li
                  key={invitation.id}
                  className="flex items-center gap-3 px-4 py-3"
                >
                  <span
                    aria-hidden
                    className="flex size-9 shrink-0 items-center justify-center rounded-full border border-dashed"
                  >
                    <MailIcon className="size-4 text-muted-foreground" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">
                      {invitation.email}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Convite pendente · expira em{" "}
                      {dateFormat.format(new Date(invitation.expiresAt))}
                    </p>
                  </div>
                  <Badge variant="outline">
                    {ROLE_LABELS[invitation.role]}
                  </Badge>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </section>

      {roles.length > 0 && (
        <InviteCard organizationId={organizationId} roles={roles} />
      )}
    </div>
  )
}

function InviteCard({
  organizationId,
  roles,
}: {
  organizationId: string
  roles: Role[]
}) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<InviteInput>({
    resolver: zodResolver(inviteSchema),
    defaultValues: {
      email: "",
      role: roles.includes("PROMOTER") ? "PROMOTER" : undefined,
    },
  })

  const invite = useMutation({
    mutationFn: (values: InviteInput) => inviteMember(organizationId, values),
    onSuccess: (invitation, values) => {
      toast.success(`Convite enviado para ${invitation.email}.`)
      reset({ email: "", role: values.role })
      return queryClient.invalidateQueries({
        queryKey: ["team", organizationId],
      })
    },
    onError: (error) =>
      setFormError(applyApiError(error, setError, ["email", "role"])),
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(null)
    invite.mutate(values)
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Convidar pessoa</CardTitle>
        <CardDescription>
          Ela recebe um link por e-mail, válido por 7 dias.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormError message={formError} />
          <TextField
            label="E-mail"
            type="email"
            inputMode="email"
            autoComplete="off"
            registration={register("email")}
            error={errors.email}
          />
          <div className="grid gap-1.5">
            <Label htmlFor="invite-role">Papel</Label>
            <Controller
              control={control}
              name="role"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger
                    id="invite-role"
                    className="h-11 w-full"
                    aria-invalid={errors.role ? true : undefined}
                  >
                    <SelectValue placeholder="Escolha um papel" />
                  </SelectTrigger>
                  <SelectContent>
                    {roles.map((role) => (
                      <SelectItem key={role} value={role} className="min-h-10">
                        {ROLE_LABELS[role]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.role && (
              <p role="alert" className="text-sm text-destructive">
                {errors.role.message}
              </p>
            )}
          </div>
          <Button
            type="submit"
            size="lg"
            className="h-11"
            disabled={invite.isPending}
          >
            {invite.isPending ? "Enviando…" : "Enviar convite"}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
