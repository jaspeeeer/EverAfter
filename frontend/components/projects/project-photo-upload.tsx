"use client";

import { useActionState, useEffect, useRef, useTransition } from "react";
import { ImagePlus, Trash2 } from "lucide-react";
import {
  removeProjectPhotoAction,
  setProjectPhotoAction,
  type ActionState,
  type ProjectPhotoSlot,
} from "@/app/actions/projects";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";

const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
const MAX_BYTES = 10 * 1024 * 1024;

const initialState: ActionState = {};

/**
 * A project has exactly one photo per slot (cover, ceremony, reception) — this control
 * uploads/replaces it and can remove it, separate from the main settings form since it submits
 * as multipart rather than the form's own JSON PUT. Reused for all three slots; `label` names
 * the slot in the button/toast copy (e.g. "cover photo", "ceremony photo").
 */
export function ProjectPhotoUpload({
  projectId,
  slot,
  label,
  hasPhoto,
}: {
  projectId: string;
  slot: ProjectPhotoSlot;
  label: string;
  hasPhoto: boolean;
}) {
  const [state, formAction, pending] = useActionState(
    setProjectPhotoAction.bind(null, slot, projectId),
    initialState,
  );
  const [removing, startRemoving] = useTransition();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(`${capitalize(label)} updated`, "success");
      formRef.current?.reset();
    } else if (state.error) {
      toast(state.error, "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!ALLOWED_TYPES.has(file.type)) {
      toast("Use a JPEG, PNG, GIF, or WebP image.", "error");
      e.target.value = "";
      return;
    }
    if (file.size > MAX_BYTES) {
      toast("That file is larger than 10 MB.", "error");
      e.target.value = "";
      return;
    }
    formRef.current?.requestSubmit();
  }

  function remove() {
    startRemoving(async () => {
      const res = await removeProjectPhotoAction(slot, projectId);
      if (res.error) toast(res.error, "error");
      else toast(`${capitalize(label)} removed`, "success");
    });
  }

  return (
    <div>
      <form ref={formRef} action={formAction} className="flex items-center gap-2">
        <input
          ref={fileInputRef}
          type="file"
          name="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          className="hidden"
          onChange={handleFileChange}
        />
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={pending}
          onClick={() => fileInputRef.current?.click()}
          aria-label={hasPhoto ? `Replace ${label}` : `Upload ${label}`}
        >
          <ImagePlus />
          {pending ? "Uploading…" : hasPhoto ? "Replace photo" : "Upload photo"}
        </Button>
        {hasPhoto && (
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={removing}
            onClick={remove}
            aria-label={`Remove ${label}`}
          >
            <Trash2 />
            Remove
          </Button>
        )}
      </form>
    </div>
  );
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
