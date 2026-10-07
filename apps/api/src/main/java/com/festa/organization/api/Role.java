package com.festa.organization.api;

/** Papéis fixos por organização (docs/PLAN.md M1). Os valores batem com o CHECK de organization_members.role. */
public enum Role {
	OWNER,
	ADMIN,
	MANAGER,
	PROMOTER,
	CHECKIN_OPERATOR
}
