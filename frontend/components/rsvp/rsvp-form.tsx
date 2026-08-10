"use client";

import { useActionState } from "react";
import { CheckCircle2 } from "lucide-react";
import { submitRsvpAction } from "@/app/actions/invitations";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { humanizeEnum } from "@/lib/format";
import type { RsvpStatus, RsvpViewResponse } from "@/lib/types";

const RSVP_CHOICES: { value: RsvpStatus; label: string }[] = [
  { value: "ATTENDING", label: "Joyfully accepts" },
  { value: "DECLINED", label: "Regretfully declines" },
  { value: "MAYBE", label: "Not sure yet" },
];

export function RsvpForm({
  token,
  rsvp,
}: {
  token: string;
  rsvp: RsvpViewResponse;
}) {
  const [state, action, pending] = useActionState(
    submitRsvpAction.bind(null, token),
    {},
  );

  if (state.ok) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
          <CheckCircle2 className="size-10 text-success" />
          <h2 className="text-xl font-semibold">Thank you, {rsvp.guestName}!</h2>
          <p className="text-sm text-muted-foreground">
            Your RSVP has been recorded. You can revisit this link any time to change it.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Hello, {rsvp.guestName}</CardTitle>
        <CardDescription>
          {rsvp.rsvpStatus === "PENDING"
            ? "Please let the couple know if you can make it."
            : `Your current reply: ${humanizeEnum(rsvp.rsvpStatus)}. You can update it below.`}
        </CardDescription>
      </CardHeader>
      <form action={action}>
        <CardContent className="space-y-4">
          {state.error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {state.error}
            </p>
          )}

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Will you attend?</legend>
            {RSVP_CHOICES.map((choice) => (
              <label
                key={choice.value}
                className="flex cursor-pointer items-center gap-3 rounded-lg border border-border px-4 py-3 text-sm transition-colors hover:bg-muted has-[:checked]:border-primary has-[:checked]:bg-primary/5"
              >
                <input
                  type="radio"
                  name="rsvpStatus"
                  value={choice.value}
                  defaultChecked={
                    rsvp.rsvpStatus === choice.value ||
                    (rsvp.rsvpStatus === "PENDING" && choice.value === "ATTENDING")
                  }
                  className="size-4 accent-[var(--primary)]"
                />
                {choice.label}
              </label>
            ))}
          </fieldset>

          <div className="space-y-1.5">
            <Label htmlFor="dietaryNotes">Dietary needs (optional)</Label>
            <Textarea
              id="dietaryNotes"
              name="dietaryNotes"
              placeholder="Vegetarian, allergies…"
              defaultValue={rsvp.dietaryNotes ?? ""}
            />
          </div>
        </CardContent>
        <CardFooter>
          <Button type="submit" disabled={pending} className="w-full">
            {pending ? "Sending…" : "Send RSVP"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
