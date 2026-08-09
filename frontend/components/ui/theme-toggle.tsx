"use client";

import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
import { Button } from "./button";

/**
 * Toggles the `dark` class on <html> and persists the choice. A small inline script in the root
 * layout applies the saved/system theme before paint, so this only needs to reflect and flip it.
 */
export function ThemeToggle() {
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    setIsDark(document.documentElement.classList.contains("dark"));
  }, []);

  const toggle = () => {
    const next = !isDark;
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem("theme", next ? "dark" : "light");
    } catch {
      // ignore storage failures (e.g. private mode)
    }
    setIsDark(next);
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title="Toggle theme"
    >
      {isDark ? <Sun /> : <Moon />}
    </Button>
  );
}
