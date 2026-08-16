import "server-only";

import { apiFetch } from "./api";
import { getToken } from "./session";
import type {
  ActivityEntityType,
  ActivityLogResponse,
  AdminUserResponse,
  AttachmentOwnerType,
  AttachmentResponse,
  BookingConversionReport,
  BudgetSummaryResponse,
  ChecklistTemplateResponse,
  EntourageMemberResponse,
  ExpenseResponse,
  GuestResponse,
  GuestRoleResponse,
  InDemandVendorRow,
  InvitationPublicResponse,
  InvitationResponse,
  NotificationPreferencesResponse,
  NotificationResponse,
  PlatformStatsResponse,
  ProjectResponse,
  RsvpViewResponse,
  TaskResponse,
  TimelineEventResponse,
  VendorCategoryResponse,
  VendorDirectoryResponse,
  VendorResponse,
  VendorsByCategoryRow,
  VendorTemplateResponse,
} from "./types";

async function authedGet<T>(path: string): Promise<T> {
  const token = await getToken();
  return apiFetch<T>(path, { token });
}

export const getProjects = () => authedGet<ProjectResponse[]>("/api/projects");

export const getProject = (id: string) =>
  authedGet<ProjectResponse>(`/api/projects/${id}`);

export const getTasks = (id: string) =>
  authedGet<TaskResponse[]>(`/api/projects/${id}/tasks`);

export const getVendors = (id: string) =>
  authedGet<VendorResponse[]>(`/api/projects/${id}/vendors`);

export const getBudget = (id: string) =>
  authedGet<BudgetSummaryResponse>(`/api/projects/${id}/budget`);

export const getExpenses = (id: string) =>
  authedGet<ExpenseResponse[]>(`/api/projects/${id}/expenses`);

export const getGuests = (id: string) =>
  authedGet<GuestResponse[]>(`/api/projects/${id}/guests`);

export const getInvitations = (id: string) =>
  authedGet<InvitationResponse[]>(`/api/projects/${id}/invitations`);

// --- Public (unauthenticated) fetchers, keyed by secret tokens ---

export const getPublicRsvp = (token: string) =>
  apiFetch<RsvpViewResponse>(`/api/public/rsvp/${token}`);

export const getPublicInvitation = (token: string) =>
  apiFetch<InvitationPublicResponse>(`/api/public/invitations/${token}`);

export const getTimeline = (id: string) =>
  authedGet<TimelineEventResponse[]>(`/api/projects/${id}/timeline`);

export const getEntourage = (id: string) =>
  authedGet<EntourageMemberResponse[]>(`/api/projects/${id}/entourage`);

// --- Templates (browse: planner/admin) ---

export const getChecklistTemplates = () =>
  authedGet<ChecklistTemplateResponse[]>("/api/templates/checklist");

export const getVendorTemplates = () =>
  authedGet<VendorTemplateResponse[]>("/api/templates/vendors");

// --- Vendor categories & directory ---

export const getVendorCategories = () =>
  authedGet<VendorCategoryResponse[]>("/api/vendor-categories");

export const getAdminVendorCategories = () =>
  authedGet<VendorCategoryResponse[]>("/api/admin/vendor-categories");

export const getGuestRoles = () => authedGet<GuestRoleResponse[]>("/api/guest-roles");

export const getAdminGuestRoles = () =>
  authedGet<GuestRoleResponse[]>("/api/admin/guest-roles");

export const getVendorDirectory = () =>
  authedGet<VendorDirectoryResponse[]>("/api/vendor-directory");

export const getAdminVendorDirectory = () =>
  authedGet<VendorDirectoryResponse[]>("/api/admin/vendor-directory");

// --- Admin ---

export const getAdminUsers = () => authedGet<AdminUserResponse[]>("/api/admin/users");

export const getAdminStats = () => authedGet<PlatformStatsResponse>("/api/admin/stats");

// --- Reports ---

export const getVendorsByCategoryReport = () =>
  authedGet<VendorsByCategoryRow[]>("/api/admin/reports/vendors-by-category");

export const getBookingConversionReport = () =>
  authedGet<BookingConversionReport>("/api/admin/reports/booking-conversion");

// --- Notifications ---

export const listNotifications = (unreadOnly = false, limit?: number) => {
  const query = new URLSearchParams();
  if (unreadOnly) query.set("unreadOnly", "true");
  if (limit) query.set("limit", String(limit));
  const qs = query.toString();
  return authedGet<NotificationResponse[]>(
    `/api/notifications${qs ? `?${qs}` : ""}`,
  );
};

export const getUnreadNotificationCount = () =>
  authedGet<{ count: number }>("/api/notifications/unread-count");

export const getNotificationPreferences = () =>
  authedGet<NotificationPreferencesResponse>("/api/notification-preferences");

// --- Activity log ---

export const getActivity = (
  projectId: string,
  params: { entityType?: ActivityEntityType; actorId?: string; limit?: number } = {},
) => {
  const query = new URLSearchParams();
  if (params.entityType) query.set("entityType", params.entityType);
  if (params.actorId) query.set("actorId", params.actorId);
  if (params.limit) query.set("limit", String(params.limit));
  const qs = query.toString();
  return authedGet<ActivityLogResponse[]>(
    `/api/projects/${projectId}/activity${qs ? `?${qs}` : ""}`,
  );
};

// --- Attachments ---

export const getAttachments = (
  projectId: string,
  ownerType: AttachmentOwnerType,
  ownerId: string,
) => {
  const query = new URLSearchParams({ ownerType, ownerId });
  return authedGet<AttachmentResponse[]>(
    `/api/projects/${projectId}/attachments?${query.toString()}`,
  );
};

export const getInDemandVendorsReport = (
  params: { from?: string; to?: string; categoryId?: string } = {},
) => {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.categoryId) query.set("categoryId", params.categoryId);
  const qs = query.toString();
  return authedGet<InDemandVendorRow[]>(
    `/api/admin/reports/in-demand-vendors${qs ? `?${qs}` : ""}`,
  );
};
