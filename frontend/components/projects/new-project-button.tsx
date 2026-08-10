"use client";

import { useActionState, useState } from "react";
import { Plus } from "lucide-react";
import { createProjectAction } from "@/app/actions/projects";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";

export function NewProjectButton() {
  const [open, setOpen] = useState(false);
  const [state, action, pending] = useActionState(createProjectAction, {});

  return (
    <>
      <Button onClick={() => setOpen(true)}>
        <Plus />
        New project
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="New wedding project"
        description="You'll be set as the managing planner."
      >
        <form action={action} className="space-y-4">
          {state.error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {state.error}
            </p>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="name">Project name</Label>
            <Input id="name" name="name" placeholder="The Smith Wedding" required />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="weddingDate">Wedding date</Label>
              <Input id="weddingDate" name="weddingDate" type="date" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="totalBudget">Total budget</Label>
              <Input
                id="totalBudget"
                name="totalBudget"
                type="number"
                min="0"
                placeholder="20000"
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ownerEmail">Couple&apos;s email (optional)</Label>
            <Input
              id="ownerEmail"
              name="ownerEmail"
              type="email"
              placeholder="couple@example.com"
            />
            <p className="text-xs text-muted-foreground">
              Links an existing couple account as the project owner.
            </p>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? "Creating…" : "Create project"}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
