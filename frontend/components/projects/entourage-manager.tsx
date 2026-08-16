"use client";

import { useActionState, useEffect, useRef, useTransition } from "react";
import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import {
  addEntourageMemberAction,
  moveEntourageMemberAction,
  removeEntourageMemberAction,
  type ActionState,
} from "@/app/actions/entourage";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";
import type { EntourageMemberResponse } from "@/lib/types";

const initialState: ActionState = {};

/**
 * A simple ordered {role, name} list — its own resource (own endpoints), so it lives outside
 * the main settings form the same way the Photos card does. Reordering is move-up/move-down per
 * row rather than drag-and-drop, which keeps this at "simple list" scope.
 */
export function EntourageManager({
  projectId,
  entourage,
}: {
  projectId: string;
  entourage: EntourageMemberResponse[];
}) {
  const [state, formAction, pending] = useActionState(
    addEntourageMemberAction.bind(null, projectId),
    initialState,
  );
  const [busy, startTransition] = useTransition();
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      formRef.current?.reset();
    } else if (state.error) {
      toast(state.error, "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

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
