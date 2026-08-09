import * as React from "react";
import { CalendarDays } from "lucide-react";
import { cn } from "@/lib/utils";

export interface KanbanCardProps extends React.ComponentProps<"div"> {
  title: string;
  description?: string;
  dueDate?: string;
  badge?: React.ReactNode;
}

export function KanbanCard({
  title,
  description,
  dueDate,
  badge,
  className,
  ...props
}: KanbanCardProps) {
  return (
    <div
      className={cn(
        "group cursor-grab rounded-lg border border-border bg-card p-3 shadow-sm transition-shadow hover:shadow-md active:cursor-grabbing",
        className,
      )}
      {...props}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium leading-snug text-card-foreground">
          {title}
        </p>
        {badge}
      </div>
      {description && (
        <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">
          {description}
        </p>
      )}
      {dueDate && (
        <div className="mt-2 flex items-center gap-1 text-xs text-muted-foreground">
          <CalendarDays className="size-3" />
          <span>{dueDate}</span>
        </div>
      )}
    </div>
  );
}
