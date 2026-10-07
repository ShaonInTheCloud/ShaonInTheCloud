// No credentials, access JWTs or Turnstile secret belong on this page.
export function createNativeChallenge({ nonce, send, status }) {
  if (!/^[a-f0-9]{32}$/.test(nonce || '') || typeof send !== 'function') throw new Error('Open this check from the SafeNest app.');
  let delivered = false;
  return {
    success(token) {
      if (delivered) return false;
      if (typeof token !== 'string' || !token.trim() || token.length > 2048) { this.failure(); return false; }
      delivered = true;
      send(JSON.stringify({ type: 'token', nonce, token }));
      status('complete');
      return true;
    },
    failure() { if (!delivered) status('retry'); },
    expired() { if (!delivered) status('retry'); },
  };
}

export function mountNativeChallenge({ document, window, sitekey }) {
  const params = new URLSearchParams(window.location.hash.slice(1));
  const bn = params.get('lang') === 'bn';
  const text = (en, bangla) => bn ? bangla : en;
  const label = document.querySelector('#status');
  const retry = document.querySelector('#retry');
  if (bn) {
    document.documentElement.lang = 'bn';
    document.querySelector('#heading').textContent = 'নিরাপত্তা যাচাই';
    document.querySelector('#explanation').textContent = 'অ্যাপে এগিয়ে যেতে যাচাই শেষ করুন। পাসওয়ার্ড এই যাচাই পৃষ্ঠায় পাঠানো হয় না।';
    document.querySelector('#privacy').textContent = 'স্বয়ংক্রিয় অপব্যবহার ঠেকাতে Cloudflare এই যাচাই প্রক্রিয়া করে।';
    retry.textContent = 'আবার চেষ্টা করুন';
  }
  const setStatus = state => {
    retry.hidden = state !== 'retry';
    label.textContent = state === 'complete' ? text('Check complete. Returning to SafeNest…', 'যাচাই সম্পন্ন। SafeNest-এ ফিরে যাচ্ছে…')
      : state === 'retry' ? text('Check failed or expired. Try a new check.', 'যাচাই ব্যর্থ বা মেয়াদ শেষ। নতুন করে যাচাই করুন।')
      : text('Loading security check…', 'নিরাপত্তা যাচাই লোড হচ্ছে…');
  };
  let challenge;
  try {
    challenge = createNativeChallenge({ nonce: params.get('nonce'),
      send: typeof window.SafeNestChallenge?.postMessage === 'function'
        ? value => window.SafeNestChallenge.postMessage(value) : undefined, status: setStatus });
  } catch {
    label.textContent = text('Open this check from the SafeNest app.', 'SafeNest অ্যাপ থেকে এই যাচাই খুলুন।');
    return;
  }
  let widget;
  let script;
  let timer;
  function load() {
    setStatus('loading');
    clearTimeout(timer);
    timer = setTimeout(() => challenge.failure(), 15000);
    if (script) script.remove();
    script = document.createElement('script');
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
    script.async = true;
    script.onerror = () => { clearTimeout(timer); challenge.failure(); };
    script.onload = () => {
      if (!window.turnstile) { clearTimeout(timer); challenge.failure(); return; }
      window.turnstile.ready(() => {
        clearTimeout(timer);
        try {
          widget = window.turnstile.render('#challenge', {
            // Turnstile currently has no Bangla widget locale; surrounding copy is Bangla.
            sitekey, action: 'android_auth', theme: 'light', size: 'compact', language: 'en',
            'response-field': false, callback: token => challenge.success(token),
            'expired-callback': () => challenge.expired(),
            'timeout-callback': () => challenge.failure(),
            'error-callback': () => { challenge.failure(); return true; }
          });
        } catch { challenge.failure(); }
      });
    };
    document.head.append(script);
  }
  retry.addEventListener('click', () => {
    if (widget !== undefined && window.turnstile) { setStatus('loading'); window.turnstile.reset(widget); }
    else load();
  });
  load();
}

if (typeof document !== 'undefined') mountNativeChallenge({ document, window, sitekey: __TURNSTILE_SITEKEY__ });
