import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  experimental: {
    serverActions: {
      // Default is 1MB; attachment uploads (contracts, receipts) need headroom up to the
      // backend's own 10MB cap (see app.attachments.max-file-bytes).
      bodySizeLimit: "12mb",
    },
  },
};

export default nextConfig;
