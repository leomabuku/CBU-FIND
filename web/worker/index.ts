import handler from "vinext/server/app-router-entry";

const apiPrefix = "/api/cbu";

function proxyError(referenceId: string): Response {
  return Response.json({
    ok: false,
    error: {
      code: "DEPENDENCY_UNAVAILABLE",
      message: "The secure CBU Find service is temporarily unavailable. Please retry.",
      retryable: true,
      referenceId,
    },
  }, {
    status: 503,
    headers: { "cache-control": "no-store", "x-reference-id": referenceId },
  });
}

const worker = {
  async fetch(request: Request, env: Cloudflare.Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === apiPrefix || url.pathname.startsWith(`${apiPrefix}/`)) {
      const referenceId = `WEB-PROXY-${crypto.randomUUID()}`;
      url.pathname = url.pathname.slice(apiPrefix.length) || "/";
      url.hostname = "cbu-find-api.internal";
      url.protocol = "https:";
      url.port = "";
      try {
        return await env.CBU_API.fetch(new Request(url, request));
      } catch (error) {
        console.error(JSON.stringify({
          event: "api.proxy.failed",
          referenceId,
          method: request.method,
          route: url.pathname,
          code: error instanceof Error ? error.name : "UNKNOWN",
        }));
        return proxyError(referenceId);
      }
    }
    return handler.fetch(request, env, ctx);
  },
};

export default worker;
