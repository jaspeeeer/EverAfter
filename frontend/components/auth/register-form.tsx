"use client";

import { useActionState } from "react";
import Link from "next/link";
import { registerAction } from "@/app/actions/auth";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background";

export interface RegisterInvitation {
  token: string;
  email: string;
  projectName: string;
}

export function RegisterForm({
  invitation = null,
}: {
  invitation?: RegisterInvitation | null;
}) {
  const [state, action, pending] = useActionState(registerAction, {});
  const invited = invitation !== null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{invited ? "You're invited!" : "Create your account"}</CardTitle>
        <CardDescription>
          {invited
            ? `Create your account to join "${invitation.projectName}" — the project will be linked to you automatically.`
            : "Start planning in a couple of minutes."}
        </CardDescription>
      </CardHeader>
      <form action={action}>
        <CardContent className="space-y-4">
          {state.error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {state.error}
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="firstName">First name</Label>
              <Input id="firstName" name="firstName" required />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lastName">Last name</Label>
              <Input id="lastName" name="lastName" required />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              defaultValue={invitation?.email ?? ""}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              required
            />
            <p className="text-xs text-muted-foreground">At least 8 characters.</p>
          </div>
          {invited ? (
            // Invited registrations are always couple accounts, tied to the invite token.
            <>
              <input type="hidden" name="role" value="ROLE_USER" />
              <input type="hidden" name="inviteToken" value={invitation.token} />
            </>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="role">I am a…</Label>
              <select id="role" name="role" defaultValue="ROLE_USER" className={selectClass}>
                <option value="ROLE_USER">Couple — planning our own wedding</option>
                <option value="ROLE_PLANNER">Wedding planner — managing clients</option>
              </select>
            </div>
          )}
        </CardContent>
        <CardFooter className="flex-col items-stretch gap-3">
          <Button type="submit" disabled={pending}>
            {pending ? "Creating account…" : "Create account"}
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link href="/login" className="font-medium text-primary hover:underline">
              Log in
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
