package com.festa.organization.api;

/** Papéis fixos por organização (docs/PLAN.md M1). Os valores batem com o CHECK de organization_members.role. */
public enum Role {

	OWNER("Dono"),
	ADMIN("Administrador"),
	MANAGER("Gerente"),
	PROMOTER("Promoter"),
	CHECKIN_OPERATOR("Operador de check-in");

	private final String label;

	Role(String label) {
		this.label = label;
	}

	/** Nome do papel para exibir ao usuário. */
	public String label() {
		return label;
	}

}
