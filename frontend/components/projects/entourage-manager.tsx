"use client";

import { useActionState, useEffect, useMemo, useRef, useState, useTransition } from "react";
import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import {
  addEntourageMemberAction,
  importEntourageFromGuestsAction,
  moveEntourageMemberAction,
  removeEntourageMemberAction,
  type ActionState,
  type ImportActionState,
} from "@/app/actions/entourage";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";
import type { EntourageMemberResponse, GuestResponse } from "@/lib/types";

const initialState: ActionState = {};
const initialImportState: ImportActionState = {};

/**
 * A simple ordered {role, name} list — its own resource (own endpoints), so it lives outside
 * the main settings form the same way the Photos card does. Reordering is move-up/move-down per
 * row rather than drag-and-drop, which keeps this at "simple list" scope.
 */
export function EntourageManager({
  projectId,
  entourage,
  guests,
}: {
  projectId: string;
  entourage: EntourageMemberResponse[];
  guests: GuestResponse[];
}) {
  const [state, formAction, pending] = useActionState(
    addEntourageMemberAction.bind(null, projectId),
    initialState,
  );
  const [importState, importAction, importPending] = useActionState(
    importEntourageFromGuestsAction.bind(null, projectId),
    initialImportState,
  );
  const [busy, startTransition] = useTransition();
  const formRef = useRef<HTMLFormElement>(null);
  const importFormRef = useRef<HTMLFormElement>(null);
  const [selectedCount, setSelectedCount] = useState(0);
  // Bumped on a successful import to force the checkbox list to remount (see onReset below):
  // calling the native form.reset() desyncs React's internal checked-value tracker from the
  // DOM, so a later click toggles the checkbox visually but never fires onChange again.
  // Remounting with a fresh key sidesteps that rather than fighting the tracker.
  const [pickerGeneration, setPickerGeneration] = useState(0);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      formRef.current?.reset();
    } else if (state.error) {
      toast(state.error, "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  useEffect(() => {
    if (importState.ok) {
      toast(importState.ok, "success");
      // .reset() dispatches a native "reset" event, so onReset (below) syncs selectedCount
      // and remounts the checkbox list.
      importFormRef.current?.reset();
    } else if (importState.error) {
      toast(importState.error, "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [importState]);

  const eligibleByRole = useMemo(() => {
    const groups = new Map<string, GuestResponse[]>();
    for (const guest of guests) {
      if (!guest.roleEntourageEligible || !guest.roleName) continue;
      const list = groups.get(guest.roleName) ?? [];
      list.push(guest);
      groups.set(guest.roleName, list);
    }
    return groups;
  }, [guests]);

  function move(memberId: string, direction: "up" | "down") {
    startTransition(async () => {
      const res = await moveEntourageMemberAction(projectId, memberId, direction);
      if (res.error) toast(res.error, "error");
    });
  }

  function remove(memberId: string, name: string) {
    startTransition(async () => {
      const res = await removeEntourageMemberAction(projectId, memberId);
      if (res.error) toast(res.error, "error");
      else toast(`Removed "${name}" from the entourage`, "success");
    });
  }

  return (
    <div className="space-y-4">
      <form ref={formRef} action={formAction} className="flex flex-wrap items-end gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="entourage-role">Role</Label>
          <Input
            id="entourage-role"
            name="role"
            maxLength={100}
            placeholder="e.g. Best Man"
            className="w-44"
            required
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="entourage-name">Name</Label>
          <Input
            id="entourage-name"
            name="name"
            maxLength={200}
            placeholder="e.g. Juan Dela Cruz"
            className="w-56"
            required
          />
        </div>
        <Button type="submit" size="sm" variant="outline" disabled={pending}>
          {pending ? "Adding…" : "Add to entourage"}
        </Button>
      </form>

      {eligibleByRole.size > 0 ? (
        <form
          ref={importFormRef}
          action={importAction}
          onReset={() => {
            setSelectedCount(0);
            setPickerGeneration((generation) => generation + 1);
          }}
          className="space-y-3 rounded-md border border-border p-4"
        >
          <p className="text-sm font-medium">Import from guests</p>
          <div key={pickerGeneration} className="space-y-2">
            {Array.from(eligibleByRole.entries()).map(([roleName, roleGuests]) => (
              <details key={roleName} open className="rounded-md border border-border/60 p-2">
                <summary className="cursor-pointer text-sm font-medium">
                  {roleName} ({roleGuests.length})
                </summary>
                <ul className="mt-2 space-y-1 pl-1">
                  {roleGuests.map((guest) => (
                    <li key={guest.id}>
                      <label className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          name="guestId"
                          value={guest.id}
                          className="size-4 rounded border-input"
                          onChange={(e) =>
                            setSelectedCount((count) => (e.target.checked ? count + 1 : count - 1))
                          }
                        />
                        {guest.lastName ? `${guest.firstName} ${guest.lastName}` : guest.firstName}
                      </label>
                    </li>
                  ))}
                </ul>
              </details>
            ))}
          </div>
          <div className="flex items-center gap-3">
            <Button
              type="submit"
              size="sm"
              variant="outline"
              disabled={importPending || selectedCount === 0}
            >
              {importPending ? "Importing…" : "Import selected"}
            </Button>
            <span className="text-xs text-muted-foreground">{selectedCount} selected</span>
          </div>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">
          No entourage-eligible guests yet — assign an entourage role in the Guests tab, or ask
          an admin to mark a role eligible.
        </p>
      )}

      {entourage.length > 0 && (
        <ul className="divide-y divide-border rounded-md border border-border">
          {entourage.map((member, index) => (
            <li
              key={member.id}
              className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
            >
              <span>
                <span className="font-medium">{member.role}</span>
                <span className="text-muted-foreground"> — {member.name}</span>
              </span>
              <span className="flex items-center gap-1">
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  disabled={busy || index === 0}
                  onClick={() => move(member.id, "up")}
                  aria-label={`Move ${member.name} up`}
                >
                  <ArrowUp />
                </Button>
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  disabled={busy || index === entourage.length - 1}
                  onClick={() => move(member.id, "down")}
                  aria-label={`Move ${member.name} down`}
                >
                  <ArrowDown />
                </Button>
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  disabled={busy}
                  onClick={() => remove(member.id, member.name)}
                  aria-label={`Remove ${member.name} from the entourage`}
                >
                  <Trash2 />
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
