import type { GuestRoleResponse } from "@/lib/types";

/**
 * Splits a flat guest-role list into top-level roles and a parentId -> sub-roles lookup.
 * Mirrors `lib/vendor-tree.ts`'s `groupVendorsByParent` for the same one-level hierarchy shape.
 */
export function groupGuestRolesByParent(roles: GuestRoleResponse[]): {
  topLevel: GuestRoleResponse[];
  subRolesByParent: Map<string, GuestRoleResponse[]>;
} {
  const subRolesByParent = new Map<string, GuestRoleResponse[]>();
  const topLevel: GuestRoleResponse[] = [];
  for (const role of roles) {
    if (role.parentId) {
      const list = subRolesByParent.get(role.parentId) ?? [];
      list.push(role);
      subRolesByParent.set(role.parentId, list);
    } else {
      topLevel.push(role);
    }
  }
  return { topLevel, subRolesByParent };
}

/**
 * Flattens guest roles into tree order (a top-level role immediately followed by its sub-roles)
 * for a checkbox list — each entry carries whether it should render indented under its parent.
 */
export function orderGuestRolesForPicker(
  roles: GuestRoleResponse[],
): Array<{ role: GuestRoleResponse; indent: boolean }> {
  const { topLevel, subRolesByParent } = groupGuestRolesByParent(roles);
  const ordered: Array<{ role: GuestRoleResponse; indent: boolean }> = [];
  for (const parent of topLevel) {
    ordered.push({ role: parent, indent: false });
    for (const sub of subRolesByParent.get(parent.id) ?? []) {
      ordered.push({ role: sub, indent: true });
    }
  }
  return ordered;
}
