"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { AlertTriangle, GripVertical, LayoutTemplate, Plus, Trash2 } from "lucide-react";
import {
  createTaskAction,
  deleteTaskAction,
  updateTaskAction,
} from "@/app/actions/tasks";
import { applyChecklistTemplateAction } from "@/app/actions/templates";
import {
  ApplyTemplateModal,
  type TemplateOption,
} from "@/components/templates/apply-template-modal";
import { KanbanColumn } from "@/components/kanban/kanban-column";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/utils";
import { formatDate, isPastDue } from "@/lib/format";
import type {
  ChecklistTemplateResponse,
  TaskResponse,
  TaskStatus,
} from "@/lib/types";

const COLUMNS: { status: TaskStatus; title: string; accent: string }[] = [
  { status: "TODO", title: "To do", accent: "bg-muted-foreground/40" },
  { status: "IN_PROGRESS", title: "In progress", accent: "bg-accent" },
  { status: "DONE", title: "Done", accent: "bg-success" },
];

export function ChecklistBoard({
  projectId,
  tasks,
  templates = [],
  canApplyTemplates = false,
}: {
  projectId: string;
  tasks: TaskResponse[];
  templates?: ChecklistTemplateResponse[];
  canApplyTemplates?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<TaskResponse | null>(null);
  // Optimistic status overrides so a dropped card lands instantly while the server action runs.
  const [overrides, setOverrides] = useState<Record<string, TaskStatus>>({});
  const [, startTransition] = useTransition();
  const { toast } = useToast();

  // Require a small pointer movement before a drag starts, so clicks (delete) still work.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  );

  const statusOf = (task: TaskResponse): TaskStatus =>
    overrides[task.id] ?? task.status;

  const onDragStart = (event: DragStartEvent) => {
    const task = tasks.find((t) => t.id === event.active.id);
    setActiveTask(task ?? null);
  };

  const onDragEnd = (event: DragEndEvent) => {
    setActiveTask(null);
    const { active, over } = event;
    if (!over) return;
    const task = tasks.find((t) => t.id === active.id);
    const newStatus = over.id as TaskStatus;
    if (!task || statusOf(task) === newStatus) return;

    setOverrides((prev) => ({ ...prev, [task.id]: newStatus }));
    startTransition(async () => {
      try {
        await updateTaskAction(projectId, task.id, {
          title: task.title,
          description: task.description,
          status: newStatus,
          dueDate: task.dueDate,
        });
        toast("Task moved");
      } catch {
        // Roll the card back if the server rejected the move.
        setOverrides((prev) => {
          const next = { ...prev };
          delete next[task.id];
          return next;
        });
        toast("Could not move task", "error");
      }
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          Drag cards between columns to update their status.
        </p>
        <div className="flex items-center gap-2">
          {canApplyTemplates && (
            <Button size="sm" variant="outline" onClick={() => setTemplatesOpen(true)}>
              <LayoutTemplate />
              Use template
            </Button>
          )}
          <Button size="sm" onClick={() => setOpen(true)}>
            <Plus />
            Add task
          </Button>
        </div>
      </div>

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
        <div className="grid gap-4 md:grid-cols-3">
          {COLUMNS.map((col) => {
            const colTasks = tasks.filter((t) => statusOf(t) === col.status);
            return (
              <DroppableColumn
                key={col.status}
                status={col.status}
                title={col.title}
                accent={col.accent}
                count={colTasks.length}
              >
                {colTasks.map((task) => (
                  <DraggableTask key={task.id} projectId={projectId} task={task} />
                ))}
                {colTasks.length === 0 && (
                  <p className="px-1 py-6 text-center text-xs text-muted-foreground">
                    Drop tasks here.
                  </p>
                )}
              </DroppableColumn>
            );
          })}
        </div>

        <DragOverlay>
          {activeTask && (
            <div className="rotate-2 rounded-lg border border-primary/40 bg-card p-3 shadow-xl">
              <p className="text-sm font-medium">{activeTask.title}</p>
            </div>
          )}
        </DragOverlay>
      </DndContext>

      <AddTaskModal projectId={projectId} open={open} onClose={() => setOpen(false)} />

      {canApplyTemplates && (
        <ApplyTemplateModal
          open={templatesOpen}
          onClose={() => setTemplatesOpen(false)}
          title="Use a checklist template"
          description="Adds every task from the template to this project's checklist."
          confirmLabel="Add tasks"
          successNoun="task"
          templates={templates.map(
            (t): TemplateOption => ({
              id: t.id,
              name: t.name,
              description: t.description,
              itemCount: t.items.length,
              preview: t.items.map((i) =>
                i.daysBeforeWedding != null
                  ? `${i.title} — ${i.daysBeforeWedding} days before`
                  : i.title,
              ),
            }),
          )}
          onApply={(templateId) => applyChecklistTemplateAction(projectId, templateId)}
        />
      )}
    </div>
  );
}

function DroppableColumn({
  status,
  title,
  accent,
  count,
  children,
}: {
  status: TaskStatus;
  title: string;
  accent: string;
  count: number;
  children: React.ReactNode;
}) {
  const { isOver, setNodeRef } = useDroppable({ id: status });

  return (
    <div ref={setNodeRef}>
      <KanbanColumn
        title={title}
        count={count}
        accentClassName={accent}
        className={cn(
          "h-full transition-shadow",
          isOver && "ring-2 ring-primary/50 shadow-md",
        )}
      >
        {children}
      </KanbanColumn>
    </div>
  );
}

function DraggableTask({
  projectId,
  task,
}: {
  projectId: string;
  task: TaskResponse;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: task.id,
  });

  const overdue = task.status !== "DONE" && isPastDue(task.dueDate);

  const remove = () => {
    startTransition(async () => {
      await deleteTaskAction(projectId, task.id);
      toast("Task deleted");
    });
  };

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "rounded-lg border bg-card p-3 shadow-sm transition-opacity",
        overdue ? "border-destructive/40" : "border-border",
        (pending || isDragging) && "opacity-40",
      )}
    >
      <div className="flex items-start gap-2">
        <button
          type="button"
          {...listeners}
          {...attributes}
          aria-label={`Drag ${task.title}`}
          className="mt-0.5 cursor-grab touch-none text-muted-foreground/60 hover:text-muted-foreground active:cursor-grabbing"
        >
          <GripVertical className="size-4" />
        </button>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium leading-snug text-card-foreground">
            {task.title}
          </p>
          {task.description && (
            <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">
              {task.description}
            </p>
          )}
          {task.dueDate && (
            <p
              className={cn(
                "mt-2 flex items-center gap-1 text-xs",
                overdue ? "font-medium text-destructive" : "text-muted-foreground",
              )}
            >
              {overdue && <AlertTriangle className="size-3" />}
              {overdue ? "Overdue · " : "Due "}
              {formatDate(task.dueDate)}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={remove}
          disabled={pending}
          aria-label="Delete task"
          className="text-muted-foreground transition-colors hover:text-destructive"
        >
          <Trash2 className="size-4" />
        </button>
      </div>
    </div>
  );
}

function AddTaskModal({
  projectId,
  open,
  onClose,
}: {
  projectId: string;
  open: boolean;
  onClose: () => void;
}) {
  const [state, action, pending] = useActionState(
    createTaskAction.bind(null, projectId),
    {},
  );
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast("Task added");
      formRef.current?.reset();
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  return (
    <Modal open={open} onClose={onClose} title="Add task" description="Add a checklist item.">
      <form ref={formRef} action={action} className="space-y-4">
        {state.error && (
          <p
            role="alert"
            className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {state.error}
          </p>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="title">Title</Label>
          <Input id="title" name="title" placeholder="Book florist" required />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="description">Notes</Label>
          <Textarea id="description" name="description" placeholder="Optional details…" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="status">Column</Label>
            <select
              id="status"
              name="status"
              defaultValue="TODO"
              className="flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="TODO">To do</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="DONE">Done</option>
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="dueDate">Due date</Label>
            <Input id="dueDate" name="dueDate" type="date" />
          </div>
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Adding…" : "Add task"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
