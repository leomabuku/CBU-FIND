import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";

const geist = Geist({ variable: "--font-geist", subsets: ["latin"] });

export const metadata: Metadata = {
  title: { default: "CBU Find", template: "%s | CBU Find" },
  description: "Report, discover, and return lost property at Copperbelt University.",
  icons: { icon: "/cbu_find_logo.png", apple: "/cbu_find_logo.png" },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className={geist.variable}>{children}</body>
    </html>
  );
}
