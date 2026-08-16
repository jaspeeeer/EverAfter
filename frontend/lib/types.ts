// Types mirroring the Spring Boot API DTOs.

export type RoleName = "ROLE_ADMIN" | "ROLE_PLANNER" | "ROLE_USER";

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";

export type RsvpStatus = "PENDING" | "ATTENDING" | "DECLINED" | "MAYBE";

export type Gender = "MALE" | "FEMALE" | "OTHER";

export type GuestPriority = "A" | "B" | "C";

export type RelatedTo = "GROOM" | "BRIDE";

export type GuestRelationship =
  | "PARENT"
  | "IMMEDIATE_FAMILY"
  | "CLOSE_FRIEND"
  | "OFFICEMATE"
  | "RELATIVE"
  | "FAMILY_FRIEND"
  | "CHURCHMATE"
  | "COMPANION_OF_GUEST";

export interface GuestRoleResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  entourageEligible: boolean;
  /** Set when this role is a sub-role nested under a top-level one (e.g. "Candle" under "Secondary Sponsor"). */
  parentId: string | null;
  parentName: string | null;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: string;
  email: string;
  roles: RoleName[];
}

export interface UserResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: RoleName[];
}

export interface ProjectResponse {
  id: string;
  name: string;
  weddingDate: string | null;
  totalBudget: number | null;
  plannerId: string | null;
  plannerEmail: string | null;
  ownerId: string | null;
  ownerEmail: string | null;
  /** Ceremony (church) location, shown on the public invitation page. */
  ceremonyVenueName: string | null;
  ceremonyVenueAddress: string | null;
  /** Reception (venue) location. */
  receptionVenueName: string | null;
  receptionVenueAddress: string | null;
  /** "HH:mm:ss" from the backend's LocalTime, nullable. */
  ceremonyTime: string | null;
  receptionTime: string | null;
  /** When true, guests may set their own party size on the public RSVP form. Default false. */
  allowGuestPartySize: boolean;
  maxPartySize: number | null;
  coverAttachmentId: string | null;
  ceremonyPhotoAttachmentId: string | null;
  receptionPhotoAttachmentId: string | null;
  /** Short dress-code label, e.g. "Garden party formal". */
  dressCode: string | null;
  attireNotesMen: string | null;
  attireNotesWomen: string | null;
  /** Comma-separated hex colors, e.g. "#f4a5a5,#a5c4f4". */
  attirePalette: string | null;
  rsvpDeadline: string | null;
  kidsPolicy: string | null;
  /** Without the leading "#". */
  socialHashtag: string | null;
  attireMenPhotoAttachmentId: string | null;
  attireWomenPhotoAttachmentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PublicEntourageMember {
  role: string;
  name: string;
}

export interface EntourageMemberResponse {
  id: string;
  role: string;
  name: string;
  sortOrder: number;
}

export interface ImportFromGuestsResult {
  added: number;
  skippedAlreadyPresent: number;
  skippedNotEligible: number;
}

export interface TaskResponse {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  dueDate: string | null;
  projectId: string;
}

export interface VendorCategoryResponse {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

export interface VendorResponse {
  id: string;
  name: string;
  categoryId: string;
  categoryName: string;
  contactEmail: string | null;
  phone: string | null;
  booked: boolean;
  agreedPrice: number | null;
  /** Sum of recorded payments (installments) toward the agreed full amount. */
  amountPaid: number;
  directoryId: string | null;
  /** Set when this vendor is a component item nested under a package (a top-level vendor). */
  parentId: string | null;
  projectId: string;
}

export interface VendorPaymentResponse {
  id: string;
  amount: number;
  paidOn: string | null;
  dueDate: string | null;
  paid: boolean;
  note: string | null;
}

export interface VendorDirectoryResponse {
  id: string;
  name: string;
  categoryId: string;
  categoryName: string;
  contactEmail: string | null;
  phone: string | null;
  typicalPrice: number | null;
  notes: string | null;
  active: boolean;
}

export interface ExpenseResponse {
  id: string;
  description: string;
  amount: number;
  categoryId: string;
  categoryName: string;
  paid: boolean;
  /** How much of this line is paid — partial for a vendor's installment payments. */
  paidAmount: number;
  projectId: string;
  vendorId: string | null;
  vendorName: string | null;
  /** True only for the system-owned line mirroring a vendor's agreed price (read-only in the UI). */
  managed: boolean;
}

export interface GuestRoleAssignmentResponse {
  id: string;
  name: string;
  entourageEligible: boolean;
  /** Set when this role is a sub-role — the top-level role it's nested under. */
  parentName: string | null;
}

export interface GuestResponse {
  id: string;
  firstName: string;
  lastName: string | null;
  title: string | null;
  gender: Gender | null;
  email: string | null;
  phone: string | null;
  rsvpStatus: RsvpStatus;
  partySize: number | null;
  dietaryNotes: string | null;
  tableNumber: number | null;
  priority: GuestPriority | null;
  relatedTo: RelatedTo | null;
  relationship: GuestRelationship | null;
  /** A guest may carry zero, one, or several roles at once. */
  roles: GuestRoleAssignmentResponse[];
  rsvpToken: string;
  projectId: string;
}

export interface RsvpViewResponse {
  guestName: string;
  projectName: string;
  weddingDate: string | null;
  rsvpStatus: RsvpStatus;
  partySize: number;
  dietaryNotes: string | null;
  /** Ceremony (church) location. */
  ceremonyVenueName: string | null;
  ceremonyVenueAddress: string | null;
  /** Reception (venue) location. */
  receptionVenueName: string | null;
  receptionVenueAddress: string | null;
  ceremonyTime: string | null;
  receptionTime: string | null;
  /** When true, the RSVP form shows a party-size input the guest may set. */
  allowGuestPartySize: boolean;
  maxPartySize: number | null;
  /** True when the project has each photo, streamed from /api/public/rsvp/{token}/{slot}. */
  hasCover: boolean;
  hasCeremonyPhoto: boolean;
  hasReceptionPhoto: boolean;
  dressCode: string | null;
  attireNotesMen: string | null;
  attireNotesWomen: string | null;
  attirePalette: string | null;
  rsvpDeadline: string | null;
  kidsPolicy: string | null;
  socialHashtag: string | null;
  hasAttireMenPhoto: boolean;
  hasAttireWomenPhoto: boolean;
  /** Ordered, no ids — see PublicEntourageMember. */
  entourage: PublicEntourageMember[];
}

export interface InvitationResponse {
  id: string;
  email: string;
  token: string;
  status: "PENDING" | "ACCEPTED";
  projectId: string;
  createdAt: string;
  acceptedAt: string | null;
}

export interface InvitationPublicResponse {
  email: string;
  projectName: string;
  status: "PENDING" | "ACCEPTED";
}

export interface ChecklistTemplateItem {
  title: string;
  description: string | null;
  daysBeforeWedding: number | null;
}

export interface ChecklistTemplateResponse {
  id: string;
  name: string;
  description: string | null;
  items: ChecklistTemplateItem[];
}

export interface VendorTemplateItem {
  name: string;
  categoryId: string;
  categoryName: string;
}

export interface VendorTemplateResponse {
  id: string;
  name: string;
  description: string | null;
  items: VendorTemplateItem[];
}

export interface EventVendorResponse {
  id: string;
  name: string;
  categoryId: string;
  categoryName: string;
  booked: boolean;
  contactEmail: string | null;
  phone: string | null;
}

export interface TimelineEventResponse {
  id: string;
  title: string;
  description: string | null;
  location: string | null;
  /** "HH:mm:ss" from the backend's LocalTime. */
  startTime: string;
  endTime: string | null;
  vendors: EventVendorResponse[];
  projectId: string;
}

export interface AdminUserResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: RoleName[];
  enabled: boolean;
}

export interface PlatformStatsResponse {
  totalUsers: number;
  usersByRole: Record<string, number>;
  totalProjects: number;
  totalTasks: number;
  totalVendors: number;
  totalExpenses: number;
  totalGuests: number;
}

// --- Vendor reports ---

export interface VendorsByCategoryRow {
  categoryId: string;
  categoryName: string;
  vendorCount: number;
  bookedCount: number;
  totalAgreedValue: number;
}

export interface InDemandVendorRow {
  vendorName: string;
  categoryName: string;
  usageCount: number;
  bookedCount: number;
  totalAgreedValue: number;
  fromDirectory: boolean;
}

export interface BookingConversionRow {
  categoryName: string;
  considered: number;
  booked: number;
  bookedRate: number;
}

export interface BookingConversionReport {
  categories: BookingConversionRow[];
  totalConsidered: number;
  totalBooked: number;
  overallRate: number;
}

export interface BudgetSummaryResponse {
  projectId: string;
  totalBudget: number | null;
  totalExpenses: number;
  totalPaid: number;
  totalOutstanding: number;
  remaining: number | null;
  overBudget: boolean;
}

// --- Notifications ---

export type NotificationType =
  | "TASK_DUE_SOON"
  | "PAYMENT_DUE_SOON"
  | "WEDDING_COUNTDOWN";

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  linkPath: string | null;
  entityType: string | null;
  entityId: string | null;
  projectId: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPreferencesResponse {
  inappTaskDue: boolean;
  inappPaymentDue: boolean;
  inappCountdown: boolean;
}

// --- Activity log ---

export type ActivityEntityType =
  | "PROJECT"
  | "TASK"
  | "VENDOR"
  | "VENDOR_PAYMENT"
  | "EXPENSE"
  | "GUEST"
  | "INVITATION"
  | "TIMELINE_EVENT"
  | "ATTACHMENT"
  | "ENTOURAGE_MEMBER";

export type ActivityAction = "CREATE" | "UPDATE" | "DELETE" | "RESTORE";

export interface ActivityLogResponse {
  id: string;
  projectId: string;
  actorUserId: string | null;
  actorEmail: string | null;
  entityType: ActivityEntityType;
  entityId: string | null;
  action: ActivityAction;
  summary: string;
  createdAt: string;
}

// --- Attachments ---

export type AttachmentOwnerType = "VENDOR" | "VENDOR_PAYMENT" | "EXPENSE" | "PROJECT";

export interface AttachmentResponse {
  id: string;
  projectId: string;
  ownerType: AttachmentOwnerType;
  ownerId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: string | null;
  uploadedByEmail: string | null;
  uploadedAt: string;
}

// --- role helpers ---

export function hasRole(roles: RoleName[], role: RoleName): boolean {
  return roles.includes(role);
}

export function isAdmin(roles: RoleName[]): boolean {
  return roles.includes("ROLE_ADMIN");
}

export function isPlanner(roles: RoleName[]): boolean {
  return roles.includes("ROLE_PLANNER");
}

export function isCouple(roles: RoleName[]): boolean {
  return roles.includes("ROLE_USER");
}
