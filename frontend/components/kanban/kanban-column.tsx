import * as React from "react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";

export interface KanbanColumnProps extends React.ComponentProps<"div"> {
  title: string;
  count?: number;
  /** Tailwind background class for the accent bar, e.g. "bg-primary". */
  accentClassName?: string;
}

export function KanbanColumn({
  title,
  count,
  accentClassName = "bg-primary",
  className,
  children,
  ...props
}: KanbanColumnProps) {
  return (
    <div
      className={cn(
        "flex w-full flex-col overflow-hidden rounded-xl border border-border bg-muted/40",
        className,
      )}
      {...props}
    >
      <div className={cn("h-1 w-full", accentClassName)} />
      <div className="flex items-center justify-between px-4 py-3">
        <h4 className="font-sans text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {title}
        </h4>
        {typeof count === "number" && <Badge variant="outline">{count}</Badge>}
      </div>
      <div className="flex flex-1 flex-col gap-3 px-3 pb-3">{children}</div>
    </div>
  );
}
