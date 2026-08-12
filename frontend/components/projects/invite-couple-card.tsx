"use client";

import { useActionState, useEffect } from "react";
import { Copy, Mail } from "lucide-react";
import { createInvitationAction } from "@/app/actions/invitations";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import type { InvitationResponse } from "@/lib/types";

/**
 * Planner-facing card: issue a couple onboarding invite and copy the resulting register link.
 * Shown on the project overview while the project has no owning couple.
 *
 * Not to be confused with the guest-facing wedding invitation (`/rsvp/[token]`,
 * `RsvpForm`/`GuestList`'s "Copy invitation link") — this is the couple's own account-creation
 * link, issued once per project.
 */
export function InviteCoupleCard({
  projectId,
  invitations,
}: {
  projectId: string;
  invitations: InvitationResponse[];
}) {
  const [state, action, pending] = useActionState(
    createInvitationAction.bind(null, projectId),
    {},
  );
  const { toast } = useToast();

  const t = useTableControls(invitations, {
    search: (i) => i.email,
    sortOptions: [
      { key: "email", label: "Email", get: (i) => i.email },
      { key: "status", label: "Status", get: (i) => i.status },
    ],
  });

  useEffect(() => {
    if (state.ok) toast("Onboarding invite created — copy the link below");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const copyLink = async (token: string) => {
    await navigator.clipboard.writeText(`${window.location.origin}/register?invite=${token}`);
    toast("Onboarding link copied — send it to the couple");
  };

  return (
    <Card className="lg:col-span-3">
      <CardHeader>
        <Mail className="size-5 text-primary" />
        <CardTitle>Couple onboarding invite</CardTitle>
        <CardDescription>
          Send the couple a link — when they register with it, this project becomes theirs to
          view and edit. (This is separate from a guest&apos;s own wedding invitation — see the
          Guests tab.)
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form action={action} className="flex flex-wrap items-end gap-3">
          {state.error && (
            <p
              role="alert"
              className="w-full rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {state.error}
            </p>
          )}
          <div className="min-w-56 flex-1 space-y-1.5">
            <Label htmlFor="invite-email">Couple&apos;s email</Label>
            <Input
              id="invite-email"
              name="email"
              type="email"
              placeholder="couple@example.com"
              required
            />
          </div>
          <Button type="submit" disabled={pending}>
            {pending ? "Creating…" : "Create invite"}
          </Button>
        </form>

        {invitations.length > 0 && (
          <div className="space-y-3">
            {invitations.length > 3 && (
              <TableToolbar>
                <SearchInput
                  value={t.query}
                  onChange={t.setQuery}
                  placeholder="Search invitations…"
                />
                <SortControl {...t} />
              </TableToolbar>
            )}

            {t.filteredCount === 0 ? (
              <p className="rounded-lg border border-dashed border-border py-6 text-center text-sm text-muted-foreground">
                No invitations match your search.
              </p>
            ) : (
              <ul className="divide-y divide-border rounded-lg border border-border">
                {t.pageItems.map((invitation) => (
                  <li key={invitation.id} className="flex items-center gap-3 px-4 py-2.5">
                    <span className="min-w-0 flex-1 truncate text-sm">{invitation.email}</span>
                    {invitation.status === "ACCEPTED" ? (
                      <Badge variant="success">Accepted</Badge>
                    ) : (
                      <>
                        <Badge variant="warning">Pending</Badge>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => copyLink(invitation.token)}
                        >
                          <Copy />
                          Copy onboarding link
                        </Button>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <Pagination {...t} />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
