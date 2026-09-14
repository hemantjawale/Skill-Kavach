export function runtimeConfig(env) {
  const production = env.NODE_ENV === "production";
  const candidate =
    env.PUBLIC_URL || env.RENDER_EXTERNAL_URL || "http://localhost:8080";
  let url;
  try {
    url = new URL(candidate);
  } catch {
    throw new Error("PUBLIC_URL must be an absolute URL.");
  }
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== "/"
  )
    throw new Error(
      "PUBLIC_URL must be an HTTP(S) origin without credentials, path, query or fragment.",
    );
  if (
    production &&
    (url.protocol !== "https:" ||
      ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname))
  )
    throw new Error("Production requires a public HTTPS origin.");
  const port = Number(env.PORT || 8080);
  if (!Number.isInteger(port) || port < 1 || port > 65535)
    throw new Error("PORT must be between 1 and 65535.");
  const proxy = env.PROXY_HOPS || "0";
  if (!["0", "1"].includes(proxy))
    throw new Error("PROXY_HOPS must be 0 or 1.");
  if (!env.DATABASE_URL) throw new Error("DATABASE_URL is required.");
  return { publicUrl: url.origin, port, proxyHops: Number(proxy) };
}
