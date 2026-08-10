import { getGuestRoles, getGuests } from "@/lib/data";
import { GuestList } from "@/components/guests/guest-list";

export default async function GuestsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [guests, roles] = await Promise.all([getGuests(id), getGuestRoles()]);

  return <GuestList projectId={id} guests={guests} roles={roles} />;
}
