"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

export function ProjectTabs({ projectId }: { projectId: string }) {
  const pathname = usePathname();
  const base = `/projects/${projectId}`;
  const tabs = [
    { href: base, label: "Overview" },
    { href: `${base}/checklist`, label: "Checklist" },
    { href: `${base}/timeline`, label: "Timeline" },
    { href: `${base}/vendors`, label: "Vendors" },
    { href: `${base}/budget`, label: "Budget" },
    { href: `${base}/guests`, label: "Guests" },
    { href: `${base}/activity`, label: "Activity" },
  ];

  return (
    <nav className="flex gap-1 overflow-x-auto">
      {tabs.map((tab) => {
        const active =
          tab.href === base ? pathname === base : pathname.startsWith(tab.href);
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={cn(
              "-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors",
              active
                ? "border-primary text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
