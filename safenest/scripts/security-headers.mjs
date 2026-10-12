export function contentSecurityPolicy(projectOrigin, { meta = false } = {}) {
  const origin = new URL(projectOrigin);
  // Cookieless Cloudflare Web Analytics, only when a beacon token is configured.
  const analytics = /^[0-9a-f]{32}$/.test(process.env.CF_ANALYTICS_TOKEN || '');
  const analyticsScript = analytics ? ' https://static.cloudflareinsights.com' : '';
  const analyticsConnect = analytics ? ' https://cloudflareinsights.com' : '';
  if (origin.protocol !== 'https:' || origin.origin !== projectOrigin) throw new Error('Invalid backend origin');
  const policy = `default-src 'none'; script-src 'self' https://challenges.cloudflare.com${analyticsScript}; frame-src https://challenges.cloudflare.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; media-src 'self'; connect-src 'self' ${origin.origin}${analyticsConnect}; upgrade-insecure-requests; base-uri 'none'; form-action 'none'; object-src 'none'; frame-ancestors 'none'`;
  return meta ? policy.replace("; frame-ancestors 'none'", '') : policy;
}
export function securityHeaders(projectOrigin) {
  const policy = contentSecurityPolicy(projectOrigin);
  let headers = `/*\n  Strict-Transport-Security: max-age=31536000\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: strict-origin-when-cross-origin\n  X-Frame-Options: DENY\n  Permissions-Policy: camera=(), microphone=(), geolocation=()\n  Content-Security-Policy: ${policy}\n`;
  // Static hosts can serve /account.html as /account. Protect both names.
  for (const route of ['/account', '/account/', '/account.html', '/delete-account', '/delete-account.html']) {
    headers += `${route}\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n`;
  }
  return headers + '/account.js\n  Cache-Control: no-cache\n';
}
