import { createClient } from '@supabase/supabase-js';
import { nextAccountState, mayShowProfile } from './account-state.js';

const $ = (id) => document.getElementById(id);
let language = 'en';
try { language = localStorage.getItem('safenest-language-v2') === 'bn' ? 'bn' : 'en'; } catch {}

function translate(element) {
  const en = document.createElement('span');
  en.className = 'translation-en'; en.lang = 'en'; en.textContent = element.dataset.en;
  const bn = document.createElement('span');
  bn.className = 'translation-bn'; bn.lang = 'bn'; bn.textContent = element.dataset.bn;
  element.replaceChildren(en, bn);
}
function setLanguage(value) {
  language = value === 'bn' ? 'bn' : 'en';
  document.documentElement.lang = language;
  document.body.classList.toggle('bn', language === 'bn');
  document.querySelectorAll('[data-language]').forEach(button => {
    button.setAttribute('aria-pressed', String(button.dataset.language === language));
  });
  try { localStorage.setItem('safenest-language-v2', language); } catch {}
}
function message(en, bn, error = false) {
  const node = $('status');
  node.hidden = !en;
  node.dataset.en = en; node.dataset.bn = bn;
  node.classList.toggle('error', error);
  translate(node);
}
function setBusy(form, busy) {
  form.setAttribute('aria-busy', String(busy));
  form.querySelectorAll('input, select, button').forEach(input => { input.disabled = busy; });
}
function errorMessage(error) {
  const errors = {
    invalid_credentials: ['The email or password is incorrect.', 'ইমেইল বা পাসওয়ার্ড সঠিক নয়।'],
    invalid_login_credentials: ['The email or password is incorrect.', 'ইমেইল বা পাসওয়ার্ড সঠিক নয়।'],
    email_not_confirmed: ['Confirm your email before logging in.', 'লগইনের আগে আপনার ইমেইল নিশ্চিত করুন।'],
    weak_password: ['Choose a stronger password with at least 10 characters.', 'কমপক্ষে ১০ অক্ষরের আরও শক্তিশালী পাসওয়ার্ড দিন।'],
    same_password: ['Choose a password different from your current password.', 'বর্তমান পাসওয়ার্ডের থেকে আলাদা পাসওয়ার্ড দিন।'],
    over_email_send_rate_limit: ['Too many email requests. Please try again later.', 'অনেকবার ইমেইল চাওয়া হয়েছে। পরে আবার চেষ্টা করুন।'],
    over_request_rate_limit: ['Please wait a little before trying again.', 'আবার চেষ্টা করার আগে কিছুক্ষণ অপেক্ষা করুন।'],
    email_address_not_authorized: ['Account emails are still being set up. Please try again later.', 'অ্যাকাউন্টের ইমেইল সেবা এখনো প্রস্তুত হচ্ছে। পরে আবার চেষ্টা করুন।'],
    user_already_exists: ['Try logging in, or use Forgot your password.', 'লগইন করুন অথবা পাসওয়ার্ড রিসেট করুন।']
  };
  const text = errors[error?.code] || ['We couldn’t complete that request. Please try again.', 'অনুরোধটি সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।'];
  message(...text, true);
}

document.querySelectorAll('[data-en][data-bn]').forEach(translate);
setLanguage(language);
document.querySelectorAll('[data-language]').forEach(button => {
  button.addEventListener('click', () => setLanguage(button.dataset.language));
});

async function start() {
  if (!__SUPABASE_KEY__) {
    message('Account setup is in progress. Please check back soon.', 'অ্যাকাউন্ট সেবা প্রস্তুত হচ্ছে। শীঘ্রই আবার দেখুন।');
    return;
  }
  const client = createClient(__SUPABASE_URL__, __SUPABASE_KEY__, {
    auth: {
      persistSession: true, autoRefreshToken: true, detectSessionInUrl: true,
      // This static, browser-only page consumes email confirmation/recovery links.
      flowType: 'implicit'
    }
  });
  const accountUrl = new URL('account.html', location.href);
  let state = {
    user: null, view: 'login',
    recovering: new URLSearchParams(location.search).get('mode') === 'recovery'
  };
  let revision = 0;
  let observedAuthEvent = false;
  const linkHasError = new URLSearchParams(location.hash.slice(1)).has('error');

  function chooseMode(mode) {
    for (const name of ['login', 'signup', 'reset']) $(name + '-form').hidden = name !== mode;
    document.querySelectorAll('[data-mode]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.mode === mode));
    });
    message('', '');
  }
  document.querySelectorAll('[data-mode]').forEach(button => {
    button.addEventListener('click', () => chooseMode(button.dataset.mode));
  });

  async function loadProfile(userId, requestRevision) {
    try {
      const { data, error } = await client.from('profiles')
        .select('display_name,language').eq('id', userId).maybeSingle();
      if (!mayShowProfile(state, userId, requestRevision, revision)) return;
      if (error) {
        message('You are logged in, but your profile could not load. Please reload to try again.', 'লগইন হয়েছে, তবে প্রোফাইল লোড হয়নি। আবার লোড করে চেষ্টা করুন।', true);
        return;
      }
      $('profile-form').elements.display_name.value = data?.display_name || '';
      $('profile-form').elements.language.value = data?.language || language;
    } catch {
      if (mayShowProfile(state, userId, requestRevision, revision)) {
        message('Your profile could not load. Please reload to try again.', 'প্রোফাইল লোড হয়নি। আবার লোড করে চেষ্টা করুন।', true);
      }
      return;
    }
    if (mayShowProfile(state, userId, requestRevision, revision)) setBusy($('profile-form'), false);
  }

  function showSession(event, session) {
    const previousId = state.user?.id;
    state = nextAccountState(state, event, session);
    const identityChanged = previousId !== state.user?.id;
    if (identityChanged) revision++;
    $('guest').hidden = state.view !== 'login';
    $('account').hidden = state.view !== 'account';
    $('recovery').hidden = state.view !== 'recovery';
    if (!state.user) {
      $('user-email').textContent = '';
      $('profile-form').reset();
      if (event === 'SIGNED_OUT') {
        document.querySelectorAll('form').forEach(form => form.reset());
        history.replaceState(null, '', accountUrl.pathname);
        chooseMode('login');
      }
    } else {
      $('user-email').textContent = state.user.email || '';
      if (identityChanged) {
        $('profile-form').reset();
        setBusy($('profile-form'), true);
        const requestRevision = revision;
        const requestedUserId = state.user.id;
        // Keep Supabase calls outside the auth listener's synchronous lock.
        setTimeout(() => { void loadProfile(requestedUserId, requestRevision); }, 0);
      }
    }
  }

  client.auth.onAuthStateChange((event, session) => {
    if (!observedAuthEvent) message('', '');
    observedAuthEvent = true;
    showSession(event, session);
    if (event === 'PASSWORD_RECOVERY') {
      history.replaceState(null, '', accountUrl.pathname + '?mode=recovery');
      message('Your reset link is confirmed. Choose a new password.', 'রিসেট লিংক নিশ্চিত হয়েছে। নতুন পাসওয়ার্ড দিন।');
    }
  });

  function bindForm(id, action, guest = false) {
    const form = $(id);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (!form.reportValidity() || form.getAttribute('aria-busy') === 'true') return;
      const values = Object.fromEntries(new FormData(form));
      if (values.confirmation !== undefined && values.password !== values.confirmation) {
        message('The passwords do not match.', 'পাসওয়ার্ড দুটি এক নয়।', true);
        return;
      }
      setBusy(form, true);
      if (guest) document.querySelectorAll('[data-mode]').forEach(button => { button.disabled = true; });
      message('Please wait…', 'একটু অপেক্ষা করুন…');
      try { await action(values, form); } catch (error) { errorMessage(error); }
      finally {
        form.querySelectorAll('input[type="password"]').forEach(input => { input.value = ''; });
        setBusy(form, false);
        if (guest) document.querySelectorAll('[data-mode]').forEach(button => { button.disabled = false; });
      }
    });
  }

  bindForm('login-form', async ({ email, password }) => {
    const { error } = await client.auth.signInWithPassword({ email: email.trim(), password });
    if (error) throw error;
    message('You are logged in.', 'আপনি লগইন করেছেন।');
  }, true);

  bindForm('signup-form', async ({ email, password }, form) => {
    const { data, error } = await client.auth.signUp({
      email: email.trim(), password,
      options: { emailRedirectTo: accountUrl.href }
    });
    if (error) throw error;
    form.reset();
    if (data.session) message('Your account is ready.', 'আপনার অ্যাকাউন্ট প্রস্তুত।');
    else message('Check your inbox for a confirmation link. If you already have an account, log in or reset your password.', 'নিশ্চিতকরণ লিংকের জন্য ইমেইল দেখুন। আগে অ্যাকাউন্ট থাকলে লগইন বা পাসওয়ার্ড রিসেট করুন।');
  }, true);

  bindForm('reset-form', async ({ email }, form) => {
    const recoveryUrl = new URL(accountUrl); recoveryUrl.searchParams.set('mode', 'recovery');
    const { error } = await client.auth.resetPasswordForEmail(email.trim(), { redirectTo: recoveryUrl.href });
    if (error) throw error;
    form.reset();
    message('If this email has an account, a password reset link will arrive shortly.', 'এই ইমেইলে অ্যাকাউন্ট থাকলে শীঘ্রই পাসওয়ার্ড রিসেট লিংক পাবেন।');
  }, true);

  bindForm('recovery-form', async ({ password }, form) => {
    if (!state.user || !state.recovering) throw new Error('No recovery session');
    const { data, error } = await client.auth.updateUser({ password });
    if (error) throw error;
    form.reset(); state.recovering = false;
    history.replaceState(null, '', accountUrl.pathname);
    showSession('USER_UPDATED', { user: data.user });
    message('Your password has been updated.', 'আপনার পাসওয়ার্ড পরিবর্তন হয়েছে।');
  });

  bindForm('profile-form', async ({ display_name, language: selectedLanguage }) => {
    const userId = state.user?.id;
    const requestRevision = revision;
    if (!userId) throw new Error('No session');
    const values = { display_name: display_name.trim(), language: selectedLanguage === 'bn' ? 'bn' : 'en' };
    // An upsert would also update the immutable id column. Only allow updates
    // to the two user-editable columns, with the user's UUID checked by RLS.
    let { error } = await client.from('profiles').insert({ id: userId, ...values });
    if (error?.code === '23505') {
      ({ error } = await client.from('profiles').update(values).eq('id', userId));
    }
    if (error) throw error;
    if (!mayShowProfile(state, userId, requestRevision, revision)) return;
    setLanguage(values.language);
    message('Your profile is saved.', 'আপনার প্রোফাইল সংরক্ষিত হয়েছে।');
  });

  async function logout(button) {
    button.disabled = true;
    try {
      const { error } = await client.auth.signOut({ scope: 'local' });
      if (error) throw error;
      message('You have logged out on this device.', 'এই ডিভাইসে আপনি লগআউট করেছেন।');
    } catch (error) { errorMessage(error); }
    finally { button.disabled = false; }
  }
  for (const id of ['logout', 'cancel-recovery']) $(id).addEventListener('click', () => { void logout($(id)); });

  const { data, error } = await client.auth.getSession();
  if (error) throw error;
  if (!observedAuthEvent) { showSession('INITIAL_SESSION', data.session); message('', ''); }
  if (linkHasError) {
    history.replaceState(null, '', accountUrl.pathname);
    message('This email link is invalid or has expired. Request a new link.', 'এই ইমেইল লিংকটি সঠিক নয় বা মেয়াদ শেষ। নতুন লিংক নিন।', true);
  }
}

start().catch(() => {
  message('Account access is temporarily unavailable. Please reload and try again.', 'অ্যাকাউন্ট সেবা সাময়িকভাবে বন্ধ। আবার লোড করে চেষ্টা করুন।', true);
});
