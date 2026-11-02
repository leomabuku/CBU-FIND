import { DeleteAccountClient } from "./delete-account-client";

export const metadata = {
  title: "Delete your CBU Find account",
  description: "Request deletion of your CBU Find account and data without reinstalling the app.",
};

export default function DeleteAccountPage() {
  return <DeleteAccountClient />;
}
