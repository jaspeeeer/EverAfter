"use client";

import { useActionState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import type { NotificationPreferencesResponse } from "@/lib/types";
import {
  updateNotificationPreferencesAction,
  type ActionState,
} from "@/app/actions/notifications";

interface Props {
  initial: NotificationPreferencesResponse;
}

const initialState: ActionState = {};

interface ToggleProps {
  name: string;
  label: string;
  description: string;
  defaultChecked: boolean;
}

function Toggle({ name, label, description, defaultChecked }: ToggleProps) {
  return (
    <label className="flex items-start justify-between gap-4 py-3">
      <span className="flex-1">
        <span className="block text-sm font-medium">{label}</span>
        <span className="mt-0.5 block text-xs text-muted-foreground">
          {description}
        </span>
      </span>
      <input
        type="checkbox"
        name={name}
        defaultChecked={defaultChecked}
        className="mt-1 size-4 shrink-0 rounded border-border text-primary focus-visible:ring-ring"
      />
    </label>
  );
}

export function NotificationPreferencesForm({ initial }: Props) {
  const [state, formAction, isPending] = useActionState(
    updateNotificationPreferencesAction,
    initialState,
  );
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) toast("Notification preferences saved.", "success");
    else if (state.error) toast(state.error, "error");
  }, [state, toast]);

  return (
    <form action={formAction} className="space-y-1">
      <div className="divide-y divide-border">
        <Toggle
          name="inappTaskDue"
          label="Task due soon"
          description="Reminders 7, 3, and 1 day before a task's due date."
          defaultChecked={initial.inappTaskDue}
        />
        <Toggle
          name="inappPaymentDue"
          label="Vendor payment due soon"
          description="Reminders 14, 7, and 1 day before a planned installment."
          defaultChecked={initial.inappPaymentDue}
        />
        <Toggle
          name="inappCountdown"
          label="Wedding countdown"
          description="Milestone reminders at 90, 30, 7, and 1 day before the wedding."
          defaultChecked={initial.inappCountdown}
        />
      </div>
      <div className="flex justify-end pt-4">
        <Button type="submit" disabled={isPending}>
          {isPending ? "Saving…" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}
