"use client";

import { useActionState, useEffect, useState } from "react";
import { updateProjectAction } from "@/app/actions/projects";
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
import { useToast } from "@/components/ui/toast";
import type { ProjectResponse } from "@/lib/types";
import { ProjectCoverUpload } from "./project-cover-upload";

/**
 * Time inputs take an "HH:mm" string; the backend's LocalTime serializes as "HH:mm:ss" so we
 * trim the seconds off before binding, and the server accepts either form.
 */
function timeToInput(value: string | null): string {
  if (!value) return "";
  return value.length >= 5 ? value.slice(0, 5) : value;
}

export function ProjectSettingsForm({ project }: { project: ProjectResponse }) {
  const [state, action, pending] = useActionState(
    updateProjectAction.bind(null, project.id),
    {},
  );
  const { toast } = useToast();
  const [allowPartySize, setAllowPartySize] = useState(project.allowGuestPartySize);

  useEffect(() => {
    if (state.ok) toast("Settings saved", "success");
  }, [state, toast]);

  return (
    <div className="space-y-6">
    <form action={action} className="space-y-6">
      {state.error && (
        <p
          role="alert"
          className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {state.error}
        </p>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Basics</CardTitle>
          <CardDescription>
            Name, wedding date, and total budget for the project.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="name">Project name</Label>
            <Input
              id="name"
              name="name"
              defaultValue={project.name}
              required
              maxLength={200}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="weddingDate">Wedding date</Label>
            <Input
              id="weddingDate"
              name="weddingDate"
              type="date"
              defaultValue={project.weddingDate ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="totalBudget">Total budget (₱)</Label>
            <Input
              id="totalBudget"
              name="totalBudget"
              type="number"
              step="0.01"
              min="0"
              defaultValue={project.totalBudget ?? ""}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Invitation details</CardTitle>
          <CardDescription>
            Shown on the public invitation page each guest opens with their link.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="venueName">Venue name</Label>
            <Input
              id="venueName"
              name="venueName"
              defaultValue={project.venueName ?? ""}
              maxLength={200}
              placeholder="e.g. Manila Cathedral"
            />
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="venueAddress">Venue address</Label>
            <Input
              id="venueAddress"
              name="venueAddress"
              defaultValue={project.venueAddress ?? ""}
              maxLength={500}
              placeholder="Street, city — used for the guest's directions link"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ceremonyTime">Ceremony time</Label>
            <Input
              id="ceremonyTime"
              name="ceremonyTime"
              type="time"
              defaultValue={timeToInput(project.ceremonyTime)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="receptionTime">Reception time</Label>
            <Input
              id="receptionTime"
              name="receptionTime"
              type="time"
              defaultValue={timeToInput(project.receptionTime)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Guest RSVP options</CardTitle>
          <CardDescription>
            By default, party size is fixed by whatever you set per guest. Turn this on to let
            each guest report their own party size when they RSVP.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <label className="flex items-start justify-between gap-4">
            <span className="flex-1">
              <span className="block text-sm font-medium">
                Allow guests to set their own party size
              </span>
              <span className="mt-0.5 block text-xs text-muted-foreground">
                When off, the RSVP form has no party-size field and each guest&apos;s party size
                stays whatever you set for them.
              </span>
            </span>
            <input
              type="checkbox"
              name="allowGuestPartySize"
              checked={allowPartySize}
              onChange={(e) => setAllowPartySize(e.target.checked)}
              className="mt-1 size-4 shrink-0 rounded border-border text-primary focus-visible:ring-ring"
            />
          </label>
          <div className="space-y-1.5">
            <Label htmlFor="maxPartySize">Max party size per guest</Label>
            <Input
              id="maxPartySize"
              name="maxPartySize"
              type="number"
              min="1"
              step="1"
              disabled={!allowPartySize}
              defaultValue={project.maxPartySize ?? ""}
              placeholder="No limit"
              className="max-w-40"
            />
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" disabled={pending}>
          {pending ? "Saving…" : "Save settings"}
        </Button>
      </div>
    </form>

    <Card>
      <CardHeader>
        <CardTitle>Cover photo</CardTitle>
        <CardDescription>
          A banner image shown at the top of the public invitation page. Uploads immediately —
          not part of the &ldquo;Save settings&rdquo; button above.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ProjectCoverUpload projectId={project.id} hasCover={project.coverAttachmentId !== null} />
      </CardContent>
    </Card>
    </div>
  );
}
