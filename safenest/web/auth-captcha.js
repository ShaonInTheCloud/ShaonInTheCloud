// Client token handling is only a readiness check. Supabase must enforce CAPTCHA server-side.
export function captchaError() {
  return Object.assign(new Error('Complete the security check before continuing.'), { code: 'captcha_required' });
}
export function createCaptchaGate({ enabled, adapter, render }) {
  const widgets = new Map();
  return {
    async activate(form) {
      if (!enabled || widgets.has(form)) return;
      const api = await adapter();
      if (!widgets.has(form)) widgets.set(form, { api, id: render(api, form) });
    },
    async take(form) {
      if (!enabled) return undefined;
      await this.activate(form);
      const { api, id } = widgets.get(form);
      const token = api.getResponse(id);
      if (!token || api.isExpired(id)) {
        if (token) api.reset(id);
        throw captchaError();
      }
      return token;
    },
    reset(form) {
      const widget = widgets.get(form);
      if (widget) widget.api.reset(widget.id);
    }
  };
}
export function installAuthCaptcha({ enabled, sitekey }) {
  let loading;
  const adapter = () => {
    if (!loading) loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      const timer = setTimeout(() => reject(captchaError()), 15000);
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
      script.async = true;
      script.onload = () => {
        if (!window.turnstile) { clearTimeout(timer); reject(captchaError()); return; }
        window.turnstile.ready(() => { clearTimeout(timer); resolve(window.turnstile); });
      };
      script.onerror = () => { clearTimeout(timer); reject(captchaError()); };
      document.head.append(script);
    }).catch(error => { loading = undefined; throw error; });
    return loading;
  };
  return createCaptchaGate({ enabled, adapter, render(api, form) {
    const holder = form.querySelector('[data-auth-captcha]');
    holder.hidden = false;
    return api.render(holder, {
      sitekey, size: 'compact', theme: 'light', 'response-field': false,
      'expired-callback': () => {}, 'error-callback': () => true
    });
  } });
}
