"use client";

import { Printer } from "lucide-react";
import { Button } from "@/components/ui/button";

/** Triggers the browser's print dialog. Hidden on the printed output itself. */
export function PrintButton() {
  return (
    <Button
      type="button"
      onClick={() => window.print()}
      className="print:hidden"
    >
      <Printer />
      Print / Save as PDF
    </Button>
  );
}
