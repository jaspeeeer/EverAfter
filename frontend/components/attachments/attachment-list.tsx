"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { File, FileImage, Paperclip, Trash2, Upload } from "lucide-react";
import {
  deleteAttachmentAction,
  listAttachmentsAction,
  uploadAttachmentAction,
  type ActionState,
} from "@/app/actions/attachments";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { formatBytes, formatDate } from "@/lib/format";
import type { AttachmentOwnerType, AttachmentResponse } from "@/lib/types";
import { ImageLightbox } from "./image-lightbox";

const ALLOWED_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "application/pdf",
]);
const MAX_BYTES = 10 * 1024 * 1024;

function AttachmentIcon({ contentType }: { contentType: string }) {
  if (contentType.startsWith("image/")) {
    return <FileImage className="size-4 shrink-0 text-muted-foreground" aria-hidden />;
  }
  return <File className="size-4 shrink-0 text-muted-foreground" aria-hidden />;
}

interface AttachmentListProps {
  projectId: string;
  ownerType: AttachmentOwnerType;
  ownerId: string;
}

const initialState: ActionState = {};

/**
 * Files (contracts, receipts, quotes) attached to a vendor, vendor payment, or expense. Loads on
 * demand — the parent modal mounts this only once an owner id exists (a vendor must be created
 * before it can hold attachments). Anyone who can open the parent modal (admin, planner, or the
 * owning couple) can upload and delete — the backend gates every operation on the same
 * {@code canAccess} check, so a couple may attach and remove their own paperwork.
 */
export function AttachmentList({ projectId, ownerType, ownerId }: AttachmentListProps) {
  const [attachments, setAttachments] = useState<AttachmentResponse[] | null>(null);
  const [busy, startBusy] = useTransition();
  const [clientError, setClientError] = useState<string | null>(null);
  const [preview, setPreview] = useState<AttachmentResponse | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  const [state, formAction, pending] = useActionState(
    uploadAttachmentAction.bind(null, projectId, ownerType, ownerId),
    initialState,
  );

  const reload = () => {
    startBusy(async () => {
      const res = await listAttachmentsAction(projectId, ownerType, ownerId);
      if (res.error) toast(res.error, "error");
      else setAttachments(res.attachments ?? []);
    });
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerId]);

  useEffect(() => {
    if (state.ok) {
      toast("File attached");
      formRef.current?.reset();
      reload();
    } else if (state.error) {
      toast(state.error, "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    setClientError(null);
    if (!file) return;
    if (!ALLOWED_TYPES.has(file.type)) {
      setClientError("Use a PDF or an image (JPEG, PNG, GIF, WebP).");
      e.target.value = "";
      return;
    }
    if (file.size > MAX_BYTES) {
      setClientError("That file is larger than 10 MB.");
      e.target.value = "";
      return;
    }
    formRef.current?.requestSubmit();
  }

  function remove(attachmentId: string) {
    startBusy(async () => {
      const res = await deleteAttachmentAction(projectId, attachmentId);
      if (res.error) toast(res.error, "error");
      else {
        toast("Attachment removed");
        reload();
      }
    });
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1.5 text-sm font-medium">
        <Paperclip className="size-4" aria-hidden />
        Files
      </div>

      {attachments === null ? (
        <p className="text-xs text-muted-foreground">Loading files…</p>
      ) : attachments.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border px-3 py-4 text-center text-xs text-muted-foreground">
          No files attached yet.
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {attachments.map((a) => (
            <li key={a.id} className="flex items-center gap-2 px-3 py-2 text-sm">
              <AttachmentIcon contentType={a.contentType} />
              {a.contentType.startsWith("image/") ? (
                <button
                  type="button"
                  onClick={() => setPreview(a)}
                  className="min-w-0 flex-1 truncate text-left font-medium text-primary hover:underline"
                >
                  {a.filename}
                </button>
              ) : (
                <a
                  href={`/api/projects/${projectId}/attachments/${a.id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="min-w-0 flex-1 truncate font-medium text-primary hover:underline"
                >
                  {a.filename}
                </a>
              )}
              <span className="shrink-0 text-xs text-muted-foreground">
                {formatBytes(a.sizeBytes)} · {formatDate(a.uploadedAt)}
              </span>
              <button
                type="button"
                onClick={() => remove(a.id)}
                disabled={busy}
                aria-label={`Delete ${a.filename}`}
                className="shrink-0 text-muted-foreground transition-colors hover:text-destructive"
              >
                <Trash2 className="size-4" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <form ref={formRef} action={formAction}>
        <input
          ref={fileInputRef}
          type="file"
          name="file"
          accept=".pdf,.jpg,.jpeg,.png,.gif,.webp,application/pdf,image/*"
          className="hidden"
          onChange={handleFileChange}
        />
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={pending}
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload />
          {pending ? "Uploading…" : "Attach a file"}
        </Button>
        {clientError && <p className="mt-1 text-xs text-destructive">{clientError}</p>}
        <p className="mt-1 text-xs text-muted-foreground">
          PDF or image, up to 10 MB.
        </p>
      </form>

      <ImageLightbox
        open={preview !== null}
        onClose={() => setPreview(null)}
        src={preview ? `/api/projects/${projectId}/attachments/${preview.id}` : ""}
        alt={preview?.filename ?? ""}
      />
    </div>
  );
}
