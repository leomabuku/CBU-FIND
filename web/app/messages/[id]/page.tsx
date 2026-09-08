import PortalApp from "../../portal-app";
export default async function ConversationPage({ params }: { params: Promise<{ id: string }> }) { const { id } = await params; return <PortalApp initialPath={`/messages/${encodeURIComponent(id)}`} />; }
