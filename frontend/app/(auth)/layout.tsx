import { Heart } from "lucide-react";

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Heart className="size-6" />
          </div>
          <h1 className="text-3xl font-bold tracking-tight">Ever After</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Wedding planning, beautifully organized.
          </p>
        </div>
        {children}
      </div>
    </div>
  );
}
