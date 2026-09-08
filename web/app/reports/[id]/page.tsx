import PortalApp from "../../portal-app";
export default async function ReportPage({ params }: { params: Promise<{ id: string }> }) { const { id } = await params; return <PortalApp initialPath={`/reports/${encodeURIComponent(id)}`} />; }
