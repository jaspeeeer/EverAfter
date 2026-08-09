import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config. Tests run sequentially (workers: 1) because they share one backend + database;
 * per-test isolation comes from unique registration emails. The frontend dev server is started
 * by Playwright (or reused if already running); the backend + database are started out of band
 * by scripts/e2e.sh, which points the backend at an ephemeral Postgres.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 7_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120_000,
    env: {
      API_BASE_URL: process.env.API_BASE_URL ?? "http://localhost:8080",
    },
  },
});
