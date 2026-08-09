"use client";

import { useTransition } from "react";
import { setUserEnabledAction } from "@/app/actions/admin";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import type { AdminUserResponse, RoleName } from "@/lib/types";

const ROLE_LABEL: Record<RoleName, string> = {
  ROLE_ADMIN: "Admin",
  ROLE_PLANNER: "Planner",
  ROLE_USER: "Couple",
};

const ROLE_VARIANT: Record<RoleName, "accent" | "secondary" | "primary"> = {
  ROLE_ADMIN: "accent",
  ROLE_PLANNER: "secondary",
  ROLE_USER: "primary",
};

export function UserTable({
  users,
  currentUserId,
}: {
  users: AdminUserResponse[];
  currentUserId: string;
}) {
  const t = useTableControls(users, {
    search: (u) => `${u.firstName ?? ""} ${u.lastName ?? ""} ${u.email}`,
    sortOptions: [
      {
        key: "name",
        label: "Name",
        get: (u) => `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() || u.email,
      },
      { key: "email", label: "Email", get: (u) => u.email },
      { key: "status", label: "Status", get: (u) => u.enabled },
    ],
  });

  return (
    <div className="space-y-4">
      <TableToolbar>
        <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search users…" />
        <SortControl {...t} />
      </TableToolbar>

      {t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No users match your search.
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((user) => (
            <UserRow key={user.id} user={user} isSelf={user.id === currentUserId} />
          ))}
        </Card>
      )}

      <Pagination {...t} />
    </div>
  );
}

function UserRow({ user, isSelf }: { user: AdminUserResponse; isSelf: boolean }) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ");

  const toggle = () => {
    startTransition(async () => {
      const result = await setUserEnabledAction(user.id, !user.enabled);
      if (result.error) toast(result.error, "error");
      else toast(user.enabled ? `${user.email} disabled` : `${user.email} re-enabled`);
    });
  };

  return (
    <div className={cn("flex flex-wrap items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{fullName || user.email}</p>
          {user.roles.map((role) => (
            <Badge key={role} variant={ROLE_VARIANT[role] ?? "secondary"}>
              {ROLE_LABEL[role] ?? role}
            </Badge>
          ))}
          {!user.enabled && <Badge variant="destructive">Disabled</Badge>}
          {isSelf && <Badge variant="outline">You</Badge>}
        </div>
        <p className="truncate text-xs text-muted-foreground">{user.email}</p>
      </div>
      <Button
        size="sm"
        variant={user.enabled ? "outline" : "secondary"}
        onClick={toggle}
        disabled={pending || isSelf}
        title={isSelf ? "You cannot disable your own account" : undefined}
      >
        {user.enabled ? "Disable" : "Enable"}
      </Button>
    </div>
  );
}
