// Second-factor (TOTP) enrolment and step-up for privileged users: support
// operators and intelligence admins. Customers see only a small "Staff
// sign-in" link; using it adds a factor to their own account and nothing else.
//
// The database policies (supabase/pending-migrations/20261012020000_admin_operator_mfa.sql)
// require an MFA-verified session (aal2) for operator and admin access. This
// module gets a signed-in session to aal2: enrol a TOTP factor if none is
// verified yet, otherwise challenge the existing one.
//
// Pure decision logic is separated from the UI so it can be unit tested.

/** Decide what has to happen for the current session to reach aal2. */
export function planStepUp(assurance, factors) {
  if (!assurance) return { action: 'error', reason: 'assurance_unavailable' };
  if (assurance.currentLevel === 'aal2') return { action: 'done' };
  // listFactors() puts only verified TOTP factors in `totp`; unverified ones
  // (abandoned enrolments) appear only in `all`.
  const verified = (factors?.totp || []).find(f => f.status === 'verified')
    || (factors?.all || []).find(f => f.factor_type === 'totp' && f.status === 'verified');
  if (verified) return { action: 'challenge', factorId: verified.id };
  const stale = (factors?.all || []).filter(f => f.factor_type === 'totp' && f.status === 'unverified').map(f => f.id);
  return { action: 'enrol', staleFactorIds: stale };
}

/** Six digits, nothing else; an authenticator never produces anything longer. */
export function cleanCode(input) {
  const code = String(input || '').replace(/\s+/g, '');
  return /^\d{6}$/.test(code) ? code : null;
}

/**
 * Run the step-up flow.
 * @param client supabase-js client (uses client.auth.mfa.*)
 * @param ui { askCode(prompt): Promise<string|null>, showEnrol({qr, secret}): Promise<string|null>, notify(message) }
 * @returns true when the session is aal2 afterwards, false when the user cancelled or verification failed.
 */
export async function stepUp(client, ui) {
  const mfa = client.auth.mfa;
  const { data: assurance, error: aalError } = await mfa.getAuthenticatorAssuranceLevel();
  if (aalError) { ui.notify('mfa_unavailable'); return false; }
  const { data: factors, error: listError } = await mfa.listFactors();
  if (listError) { ui.notify('mfa_unavailable'); return false; }
  const plan = planStepUp(assurance, factors);
  if (plan.action === 'done') return true;
  if (plan.action === 'error') { ui.notify('mfa_unavailable'); return false; }

  if (plan.action === 'challenge') {
    const code = cleanCode(await ui.askCode('code'));
    if (!code) { ui.notify('mfa_cancelled'); return false; }
    const { error } = await mfa.challengeAndVerify({ factorId: plan.factorId, code });
    if (error) { ui.notify('mfa_wrong_code'); return false; }
    ui.notify('mfa_verified');
    return true;
  }

  // Enrol: stale unverified factors from abandoned attempts are removed first,
  // otherwise GoTrue rejects the reused friendly name.
  for (const factorId of plan.staleFactorIds || []) await mfa.unenroll({ factorId });
  const { data: enrolment, error: enrolError } = await mfa.enroll({ factorType: 'totp', friendlyName: 'SafeNest' });
  if (enrolError || !enrolment?.id) { ui.notify('mfa_unavailable'); return false; }
  const code = cleanCode(await ui.showEnrol({ qr: enrolment.totp?.qr_code, secret: enrolment.totp?.secret }));
  if (!code) { await mfa.unenroll({ factorId: enrolment.id }); ui.notify('mfa_cancelled'); return false; }
  const { error: verifyError } = await mfa.challengeAndVerify({ factorId: enrolment.id, code });
  if (verifyError) { await mfa.unenroll({ factorId: enrolment.id }); ui.notify('mfa_wrong_code'); return false; }
  ui.notify('mfa_enrolled');
  return true;
}

const MESSAGES = {
  mfa_unavailable: ['Second-factor verification is not available right now. Try again later.', 'দ্বিতীয় ধাপের যাচাই এখন সম্ভব নয়। পরে আবার চেষ্টা করুন।'],
  mfa_cancelled: ['Verification cancelled.', 'যাচাই বাতিল হয়েছে।'],
  mfa_wrong_code: ['That code was not accepted. Check the time on your phone and try again.', 'কোডটি গ্রহণ হয়নি। ফোনের সময় ঠিক আছে কি না দেখে আবার চেষ্টা করুন।'],
  mfa_verified: ['Second factor verified for this session.', 'এই সেশনের জন্য দ্বিতীয় ধাপ যাচাই হয়েছে।'],
  mfa_enrolled: ['Authenticator added and verified.', 'অথেন্টিকেটর যোগ ও যাচাই হয়েছে।']
};

/**
 * Minimal DOM UI mounted inside `host`. Kept plain so it works on the account
 * page and the admin console without extra CSS.
 */
export function domStepUpUi(host, { language = () => 'en' } = {}) {
  const text = key => { const [en, bn] = MESSAGES[key] || [key, key]; return language() === 'bn' ? bn : en; };
  const box = document.createElement('div');
  box.className = 'card mfa-step-up';
  box.hidden = true;
  host.append(box);
  const status = document.createElement('p');
  status.className = 'note';
  status.setAttribute('role', 'status');
  host.append(status);

  function prompt(render) {
    return new Promise(resolve => {
      box.replaceChildren();
      box.hidden = false;
      let node = host; while (node && node !== document.body) { if (node.hidden) node.hidden = false; node = node.parentElement; }
      const form = document.createElement('form');
      const label = document.createElement('label');
      label.textContent = language() === 'bn' ? 'অথেন্টিকেটর অ্যাপের ৬ অঙ্কের কোড' : '6-digit code from your authenticator app';
      const input = document.createElement('input');
      Object.assign(input, { type: 'text', inputMode: 'numeric', autocomplete: 'one-time-code', pattern: '[0-9]{6}', maxLength: 6, required: true, name: 'code' });
      label.append(input);
      const ok = document.createElement('button'); ok.type = 'submit'; ok.textContent = language() === 'bn' ? 'যাচাই করুন' : 'Verify';
      const cancel = document.createElement('button'); cancel.type = 'button'; cancel.textContent = language() === 'bn' ? 'বাতিল' : 'Cancel';
      render(form);
      form.append(label, ok, cancel);
      box.append(form);
      input.focus();
      const finish = value => { box.hidden = true; box.replaceChildren(); resolve(value); };
      form.addEventListener('submit', event => { event.preventDefault(); finish(input.value); });
      cancel.addEventListener('click', () => finish(null));
    });
  }

  return {
    askCode: () => prompt(() => {}),
    showEnrol: ({ qr, secret }) => prompt(form => {
      const intro = document.createElement('p');
      intro.textContent = language() === 'bn'
        ? 'স্টাফ অ্যাক্সেসের জন্য অথেন্টিকেটর অ্যাপে (Google Authenticator, Aegis, 1Password) এই কোডটি স্ক্যান করুন, তারপর ৬ অঙ্কের কোড লিখুন।'
        : 'Staff access needs a second factor. Scan this in an authenticator app (Google Authenticator, Aegis, 1Password), then enter the 6-digit code.';
      form.append(intro);
      if (qr) { const img = document.createElement('img'); img.src = qr; img.alt = 'Authenticator QR code'; img.width = 180; img.height = 180; form.append(img); }
      if (secret) { const manual = document.createElement('p'); manual.className = 'note'; manual.textContent = (language() === 'bn' ? 'হাতে লেখার কোড: ' : 'Manual entry key: ') + secret; form.append(manual); }
    }),
    notify: key => { status.textContent = text(key); }
  };
}
