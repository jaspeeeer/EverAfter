import { notFound } from "next/navigation";
import { Heart } from "lucide-react";
import { getPublicRsvp } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { RsvpForm } from "@/components/rsvp/rsvp-form";

/**
 * Public, no-login RSVP page. The unguessable token in the URL identifies the guest; everything
 * else (auth pages, app shell) stays out of this route.
 */
export default async function RsvpPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;

  let rsvp;
  try {
    rsvp = await getPublicRsvp(token);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
      notFound();
    }
    throw error;
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Heart className="size-6" />
          </div>
          <p className="text-sm uppercase tracking-widest text-muted-foreground">
            You&apos;re invited to
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight">{rsvp.projectName}</h1>
          {rsvp.weddingDate && (
            <p className="mt-1 text-sm text-muted-foreground">
              {formatDate(rsvp.weddingDate)}
            </p>
          )}
        </div>

        <RsvpForm token={token} rsvp={rsvp} />
      </div>
    </div>
  );
}
