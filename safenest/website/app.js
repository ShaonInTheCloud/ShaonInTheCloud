function setLang(lang) {
  lang = lang === 'bn' ? 'bn' : 'en';
  document.documentElement.lang = lang;
  document.body.classList.toggle('bn', lang === 'bn');
  document.querySelectorAll('[data-placeholder-en][data-placeholder-bn]').forEach(el => { el.placeholder = lang === 'bn' ? el.dataset.placeholderBn : el.dataset.placeholderEn; });
  document.querySelectorAll('[data-aria-en][data-aria-bn]').forEach(el => el.setAttribute('aria-label', lang === 'bn' ? el.dataset.ariaBn : el.dataset.ariaEn));
  document.querySelectorAll('[data-en][data-bn]').forEach(el => {
    if (lang === 'bn') { el.innerHTML = el.dataset.bn; return; }
    const en = document.createElement('span');
    en.className = 'translation-en'; en.lang = 'en'; en.innerHTML = el.dataset.en;
    const bn = document.createElement('span');
    bn.className = 'translation-bn'; bn.lang = 'bn'; bn.innerHTML = el.dataset.bn;
    el.replaceChildren(en, bn);
  });
  document.querySelectorAll('[data-lang]').forEach(el => {
    el.classList.toggle('active', el.dataset.lang === lang);
    el.setAttribute('aria-pressed', String(el.dataset.lang === lang));
  });
  if (location.pathname.endsWith('/') || location.pathname.endsWith('/index.html')) {
    document.title = lang === 'bn' ? 'SafeNest — Android-এ জুয়া ও প্রাপ্তবয়স্ক সাইট ব্লকিং' : 'SafeNest — Gambling and adult-site blocking for Android';
  }
  try { localStorage.setItem('safenest-language-v2', lang); } catch {}
}
document.querySelectorAll('[data-lang]').forEach(el => el.addEventListener('click', () => { setLang(el.dataset.lang); updateChallenge(); }));
let language = 'en';
try { language = localStorage.getItem('safenest-language-v2') || 'en'; } catch {}
setLang(language);

if ('IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
  document.body.classList.add('js-motion');
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-visible');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.09, rootMargin: '0px 0px 35px 0px' });
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
}

const challengeKey = 'safenest-practical-steps-v2';
let moments = [];
try {
  const saved = JSON.parse(localStorage.getItem(challengeKey) || '[]');
  if (Array.isArray(saved)) moments = saved.filter(n => Number.isInteger(n) && n >= 0 && n < 3);
} catch {}
function updateChallenge() {
  const label = document.querySelector('[data-progress]');
  if (!label) return;
  const count = new Set(moments).size;
  label.textContent = document.documentElement.lang === 'bn'
    ? `${count} / ৩টি কাজ চিহ্নিত করেছেন`
    : `${count} of 3 steps marked`;
  const fill = document.querySelector('[data-progress-fill]');
  if (fill) fill.style.width = `${count / 3 * 100}%`;
  document.querySelectorAll('[data-challenge]').forEach(button => {
    button.setAttribute('aria-pressed', String(moments.includes(Number(button.dataset.challenge))));
  });
}
document.querySelectorAll('[data-challenge]').forEach(button => button.addEventListener('click', () => {
  const index = Number(button.dataset.challenge);
  moments = moments.includes(index) ? moments.filter(n => n !== index) : [...moments, index];
  try { localStorage.setItem(challengeKey, JSON.stringify(moments)); } catch {}
  updateChallenge();
}));
updateChallenge();

document.querySelector('[data-share]')?.addEventListener('click', async () => {
  const status = document.querySelector('[data-share-status]');
  const url = new URL('campaign.html', location.href).href;
  try {
    if (navigator.share) await navigator.share({ title: 'SafeNest practical steps', url });
    else if (navigator.clipboard) { await navigator.clipboard.writeText(url); if (status) status.textContent = document.documentElement.lang === 'bn' ? 'লিংক কপি হয়েছে।' : 'Link copied.'; }
    else if (status) status.textContent = url;
  } catch (error) {
    if (error?.name !== 'AbortError' && status) status.textContent = url;
  }
});

// Accessible static site search: routes visitors to relevant SafeNest pages.
const searchForm = document.querySelector('[data-site-search]');
if (searchForm) {
  const searchInput = searchForm.querySelector('input[type="search"]');
  const searchResults = searchForm.querySelector('.site-search-results');
  const pages = [
    { href: './', en: 'Home', bn: 'হোম', summaryEn: 'SafeNest and the SafeNest practical steps', summaryBn: 'Android-এ জুয়া ও প্রাপ্তবয়স্ক সাইট ব্লকিং', terms: 'home safenest protect safer digital space gambling porn adult family support campaign' },
    { href: 'campaign.html', en: 'Practical steps', bn: 'করণীয়', summaryEn: 'Private checkmarks and practical steps', summaryBn: 'ব্যক্তিগত চিহ্ন ও করণীয়', terms: 'campaign challenge mindful online habits screen time recovery' },
    { href: 'how-it-works.html', en: 'Features & setup', bn: 'ফিচার ও সেটআপ', summaryEn: 'Android filtering, app guard and current limits', summaryBn: 'অ্যান্ড্রয়েড ফিল্টার, অ্যাপ গার্ড ও সীমাবদ্ধতা', terms: 'how works android dns filter website app guard vpn limits private dns encrypted security' },
    { href: 'pricing.html', en: 'Pricing', bn: 'মূল্য', summaryEn: 'Proposed prices and payment status', summaryBn: 'প্রস্তাবিত মূল্য ও পেমেন্টের অবস্থা', terms: 'pricing plans price cost subscription payment visa mastercard bkash rocket upay dbbl wallet dutch bangla bank support progress' },
    { href: 'support.html', en: 'Help', bn: 'সহায়তা', summaryEn: 'Testing, DNS troubleshooting and account help', summaryBn: 'পরীক্ষা, DNS সমস্যা ও অ্যাকাউন্টের সহায়তা', terms: 'help support contact release testing dns troubleshooting account delete privacy' },
    { href: 'our-story.html', en: 'About', bn: 'আমাদের সম্পর্কে', summaryEn: 'Why SafeNest is being built', summaryBn: 'কেন SafeNest তৈরি হচ্ছে', terms: 'story about mission why safenest recovery family' }
  ];
  const language = () => document.documentElement.lang === 'bn' ? 'bn' : 'en';
  const closeResults = () => { searchResults.hidden = true; searchInput.setAttribute('aria-expanded', 'false'); };
  const showResults = () => {
    const query = searchInput.value.trim().toLocaleLowerCase();
    searchResults.replaceChildren();
    if (!query) { closeResults(); return []; }
    const words = query.split(/\s+/).filter(Boolean);
    const matches = pages.filter(page => {
      const text = `${page.en} ${page.bn} ${page.summaryEn} ${page.summaryBn} ${page.terms}`.toLocaleLowerCase();
      return text.includes(query) || words.every(word => text.includes(word));
    });
    if (!matches.length) {
      const empty = document.createElement('div');
      empty.className = 'site-search-empty';
      empty.textContent = language() === 'bn' ? 'কোনো মিল পাওয়া যায়নি।' : 'No matching pages found.';
      searchResults.append(empty);
    } else {
      for (const page of matches) {
        const link = document.createElement('a'); link.href = page.href;
        const title = document.createElement('span'); title.textContent = page[language()];
        const summary = document.createElement('small'); summary.textContent = page[language() === 'bn' ? 'summaryBn' : 'summaryEn'];
        link.append(title, summary); searchResults.append(link);
      }
    }
    searchResults.hidden = false;
    searchInput.setAttribute('aria-expanded', 'true');
    return matches;
  };
  searchInput.addEventListener('input', showResults);
  searchInput.addEventListener('focus', () => { if (searchInput.value.trim()) showResults(); });
  searchInput.addEventListener('keydown', event => { if (event.key === 'Escape') closeResults(); });
  searchForm.addEventListener('submit', event => {
    event.preventDefault();
    const matches = showResults();
    if (matches.length) location.href = matches[0].href;
  });
  document.addEventListener('click', event => { if (!searchForm.contains(event.target)) closeResults(); });
  document.querySelectorAll('[data-lang]').forEach(button => button.addEventListener('click', () => { if (searchInput.value.trim()) showResults(); }));
}
