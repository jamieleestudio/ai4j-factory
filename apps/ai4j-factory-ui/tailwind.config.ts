import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "rgb(var(--background) / <alpha-value>)",
        foreground: "rgb(var(--foreground) / <alpha-value>)",
        sidebar: {
          DEFAULT: "rgb(var(--sidebar) / <alpha-value>)",
          foreground: "rgb(var(--sidebar-foreground) / <alpha-value>)",
        },
        "chat-bg": "rgb(var(--chat-bg) / <alpha-value>)",
        "input-bg": "rgb(var(--input-bg) / <alpha-value>)",
        "input-border": "rgb(var(--input-border) / <alpha-value>)",
        accent: "rgb(var(--accent) / <alpha-value>)",
      },
    },
  },
  plugins: [],
};
export default config;
