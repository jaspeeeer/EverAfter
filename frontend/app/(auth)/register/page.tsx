import { RegisterForm } from "@/components/auth/register-form";
import { getPublicInvitation } from "@/lib/data";
import { ApiError } from "@/lib/api";
import type { InvitationPublicResponse } from "@/lib/types";

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ invite?: string }>;
}) {
  const { invite } = await searchParams;

  let invitation: (InvitationPublicResponse & { token: string }) | null = null;
  if (invite) {
    try {
      const found = await getPublicInvitation(invite);
      if (found.status === "PENDING") {
        invitation = { ...found, token: invite };
      }
    } catch (error) {
      // Unknown/expired token: fall through to a normal registration.
      if (!(error instanceof ApiError && error.status === 404)) throw error;
    }
  }

  return <RegisterForm invitation={invitation} />;
}
