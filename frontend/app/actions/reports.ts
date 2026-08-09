"use server";

import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { InDemandVendorRow } from "@/lib/types";

/** Runs the in-demand report with the given filters (admin-only; the API enforces). */
export async function inDemandReportAction(params: {
  from?: string;
  to?: string;
  categoryId?: string;
}): Promise<{ rows?: InDemandVendorRow[]; error?: string }> {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.categoryId) query.set("categoryId", params.categoryId);
  const qs = query.toString();

  try {
    const token = await getToken();
    const rows = await apiFetch<InDemandVendorRow[]>(
      `/api/admin/reports/in-demand-vendors${qs ? `?${qs}` : ""}`,
      { token },
    );
    return { rows };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}
