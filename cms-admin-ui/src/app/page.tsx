import { redirect } from "next/navigation";

// Root "/" → always redirect to /dashboard.
// The dashboard layout handles the auth guard internally.
export default function RootPage() {
  redirect("/dashboard");
}
