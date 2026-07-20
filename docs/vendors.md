# Vendors

Supplier tracking per project: who's being considered, their contact details, and whether
they're booked.

## API

Nested under the project; all endpoints `canAccess`-gated on `{projectId}`:

- `GET /api/projects/{projectId}/vendors`
- `POST …/vendors` — `{ name, category, contactEmail?, phone?, booked }`
- `PUT …/vendors/{vendorId}` — full replace
- `DELETE …/vendors/{vendorId}`

Categories (`VendorCategory`): VENUE, CATERING, PHOTOGRAPHY, VIDEOGRAPHY, FLORIST, MUSIC,
ATTIRE, BEAUTY, STATIONERY, TRANSPORT, OTHER.

## Frontend (`/projects/[id]/vendors`)

`components/vendors/vendor-list.tsx`:

- Row list with name, category badge, contact line.
- One-click **booked toggle** (green ✓ Booked badge ↔ "Mark booked" outline).
- Add/edit share one modal (`VendorFormModal`); edit pre-fills via `defaultValue` and calls
  `editVendorAction`.
- **Name search** (`SearchInput`) filters client-side.
- Toasts on add/update/book/delete.

## Key files

- `backend/.../domain/Vendor.java`, `service/VendorService.java`, `web/VendorController.java`
- `frontend/app/actions/vendors.ts`, `components/vendors/vendor-list.tsx`
