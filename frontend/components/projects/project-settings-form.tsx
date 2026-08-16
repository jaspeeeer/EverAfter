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
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import type { EntourageMemberResponse, ProjectResponse } from "@/lib/types";
import { EntourageManager } from "./entourage-manager";
import { ProjectPhotoUpload } from "./project-photo-upload";

/**
 * Time inputs take an "HH:mm" string; the backend's LocalTime serializes as "HH:mm:ss" so we
 * trim the seconds off before binding, and the server accepts either form.
 */
function timeToInput(value: string | null): string {
  if (!value) return "";
  return value.length >= 5 ? value.slice(0, 5) : value;
}

const MAX_PALETTE_COLORS = 8;

/**
 * A repeatable row of native color swatches, joined into one comma-separated hex string on
 * submit (`attirePalette`) — the same "no independent lifecycle" reasoning as venue name/address,
 * so there's no child table, just a single delimited column.
 */
function AttirePaletteEditor({ initial }: { initial: string | null }) {
  const [colors, setColors] = useState<string[]>(
    initial
      ? initial
          .split(",")
          .map((c) => c.trim())
          .filter(Boolean)
      : [],
  );

  function updateColor(index: number, value: string) {
    setColors(colors.map((c, i) => (i === index ? value : c)));
  }

  function removeColor(index: number) {
    setColors(colors.filter((_, i) => i !== index));
  }

  return (
    <div className="space-y-2">
      <input type="hidden" name="attirePalette" value={colors.join(",")} />
      <div className="flex flex-wrap items-center gap-3">
        {colors.map((color, i) => (
          <div key={i} className="flex items-center gap-1">
            <input
              type="color"
              value={color}
              onChange={(e) => updateColor(i, e.target.value)}
              aria-label={`Palette color ${i + 1}`}
              className="size-9 cursor-pointer rounded border border-input p-0.5"
            />
            <button
              type="button"
              onClick={() => removeColor(i)}
              aria-label={`Remove palette color ${i + 1}`}
              className="text-xs text-muted-foreground hover:text-destructive"
            >
              ✕
            </button>
          </div>
        ))}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setColors([...colors, "#cccccc"])}
          disabled={colors.length >= MAX_PALETTE_COLORS}
        >
          Add color
        </Button>
      </div>
    </div>
  );
}

export function ProjectSettingsForm({
  project,
  entourage,
}: {
  project: ProjectResponse;
  entourage: EntourageMemberResponse[];
}) {
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
          <CardTitle>Ceremony</CardTitle>
          <CardDescription>
            Where and when the ceremony (e.g. the church) happens, shown on the public
            invitation page.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="ceremonyVenueName">Ceremony venue name</Label>
            <Input
              id="ceremonyVenueName"
              name="ceremonyVenueName"
              defaultValue={project.ceremonyVenueName ?? ""}
              maxLength={200}
              placeholder="e.g. Manila Cathedral"
            />
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="ceremonyVenueAddress">Ceremony venue address</Label>
            <Input
              id="ceremonyVenueAddress"
              name="ceremonyVenueAddress"
              defaultValue={project.ceremonyVenueAddress ?? ""}
              maxLength={500}
              placeholder="Street, city — used for directions and the embedded map"
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
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Reception</CardTitle>
          <CardDescription>
            Where and when the reception happens — a separate place and time from the
            ceremony.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="receptionVenueName">Reception venue name</Label>
            <Input
              id="receptionVenueName"
              name="receptionVenueName"
              defaultValue={project.receptionVenueName ?? ""}
              maxLength={200}
              placeholder="e.g. Grand Ballroom"
            />
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="receptionVenueAddress">Reception venue address</Label>
            <Input
              id="receptionVenueAddress"
              name="receptionVenueAddress"
              defaultValue={project.receptionVenueAddress ?? ""}
              maxLength={500}
              placeholder="Street, city — used for directions and the embedded map"
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

      <Card>
        <CardHeader>
          <CardTitle>Attire</CardTitle>
          <CardDescription>
            Dress-code guidance shown on the public invitation page.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="dressCode">Dress code</Label>
            <Input
              id="dressCode"
              name="dressCode"
              defaultValue={project.dressCode ?? ""}
              maxLength={200}
              placeholder="e.g. Garden party formal"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="attireNotesMen">Notes for men</Label>
            <Textarea
              id="attireNotesMen"
              name="attireNotesMen"
              defaultValue={project.attireNotesMen ?? ""}
              maxLength={500}
              placeholder="e.g. Barong or long-sleeve, dark trousers"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="attireNotesWomen">Notes for women</Label>
            <Textarea
              id="attireNotesWomen"
              name="attireNotesWomen"
              defaultValue={project.attireNotesWomen ?? ""}
              maxLength={500}
              placeholder="e.g. Cocktail-length or long dress"
            />
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label>Suggested color palette</Label>
            <AttirePaletteEditor initial={project.attirePalette} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Invitation extras</CardTitle>
          <CardDescription>
            A few small etiquette details, shown on the invitation only when set.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="rsvpDeadline">RSVP by</Label>
            <Input
              id="rsvpDeadline"
              name="rsvpDeadline"
              type="date"
              defaultValue={project.rsvpDeadline ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="socialHashtag">Social hashtag</Label>
            <Input
              id="socialHashtag"
              name="socialHashtag"
              defaultValue={project.socialHashtag ?? ""}
              maxLength={100}
              placeholder="e.g. AlexAndJamie2027 (no #)"
            />
          </div>
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="kidsPolicy">Kids policy</Label>
            <Input
              id="kidsPolicy"
              name="kidsPolicy"
              defaultValue={project.kidsPolicy ?? ""}
              maxLength={300}
              placeholder="e.g. Adults-only celebration"
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
        <CardTitle>Photos</CardTitle>
        <CardDescription>
          Shown on the public invitation page. Each uploads immediately — not part of the
          &ldquo;Save settings&rdquo; button above.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-1.5">
          <span className="block text-sm font-medium">Cover photo</span>
          <p className="text-xs text-muted-foreground">
            A banner image shown at the top of the page.
          </p>
          <ProjectPhotoUpload
            projectId={project.id}
            slot="cover"
            label="cover photo"
            hasPhoto={project.coverAttachmentId !== null}
          />
        </div>
        <div className="space-y-1.5">
          <span className="block text-sm font-medium">Ceremony photo</span>
          <p className="text-xs text-muted-foreground">
            Shown in the Ceremony section.
          </p>
          <ProjectPhotoUpload
            projectId={project.id}
            slot="ceremony-photo"
            label="ceremony photo"
            hasPhoto={project.ceremonyPhotoAttachmentId !== null}
          />
        </div>
        <div className="space-y-1.5">
          <span className="block text-sm font-medium">Reception photo</span>
          <p className="text-xs text-muted-foreground">
            Shown in the Reception section.
          </p>
          <ProjectPhotoUpload
            projectId={project.id}
            slot="reception-photo"
            label="reception photo"
            hasPhoto={project.receptionPhotoAttachmentId !== null}
          />
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle>Entourage</CardTitle>
        <CardDescription>
          The wedding party, shown on the public invitation page in this order.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <EntourageManager projectId={project.id} entourage={entourage} />
      </CardContent>
    </Card>
    </div>
  );
}
