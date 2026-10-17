// Help & support content. Numbers checked 2026-10-18 against the services' own pages:
// Kaan Pete Roi (kaanpeteroi.org/helpline-service), MIELI (mieli.fi, English line hours via
// wellbeingguide.osterbottensvalfard.fi), Peluuri (peluuri.fi/en), debt advice (peluuri.fi/en/get-help/financial-and-debt-advice).
// Re-check every few months: hours change.

export type HelpRegion = 'bd' | 'fi' | 'intl';
export type HelpContact = {name: string; detail: {en: string; bn: string}; tel?: string; url?: string};
export type HelpSection = {title: {en: string; bn: string}; body?: {en: string; bn: string}; contacts: HelpContact[]};

export const regions: {code: HelpRegion; en: string; bn: string}[] = [
  {code: 'bd', en: 'Bangladesh', bn: 'বাংলাদেশ'},
  {code: 'fi', en: 'Finland', bn: 'ফিনল্যান্ড'},
  {code: 'intl', en: 'Other countries', bn: 'অন্যান্য দেশ'},
];

const crisis: Record<HelpRegion, HelpSection> = {
  bd: {
    title: {en: 'In danger right now', bn: 'এখনই বিপদে থাকলে'},
    body: {en: 'If you might hurt yourself, call now. You do not have to be sure it is an emergency.',
      bn: 'নিজের ক্ষতি করার চিন্তা এলে এখনই ফোন করুন। জরুরি কি না নিশ্চিত হওয়ার দরকার নেই।'},
    contacts: [
      {name: '999', detail: {en: 'National emergency — police, ambulance, fire. 24/7, free.', bn: 'জাতীয় জরুরি সেবা — পুলিশ, অ্যাম্বুলেন্স, ফায়ার। ২৪ ঘণ্টা, বিনামূল্যে।'}, tel: '999'},
      {name: 'Kaan Pete Roi', detail: {en: 'Emotional support and suicide prevention. Every day 3 pm – 3 am.', bn: 'মানসিক সহায়তা ও আত্মহত্যা প্রতিরোধ। প্রতিদিন বিকেল ৩টা – রাত ৩টা।'}, tel: '+8809612119911'},
    ],
  },
  fi: {
    title: {en: 'In danger right now', bn: 'এখনই বিপদে থাকলে'},
    body: {en: 'If you might hurt yourself, call now. You do not have to be sure it is an emergency.',
      bn: 'নিজের ক্ষতি করার চিন্তা এলে এখনই ফোন করুন। জরুরি কি না নিশ্চিত হওয়ার দরকার নেই।'},
    contacts: [
      {name: '112', detail: {en: 'Emergency number. 24/7, free.', bn: 'জরুরি নম্বর। ২৪ ঘণ্টা, বিনামূল্যে।'}, tel: '112'},
      {name: 'MIELI Crisis Helpline', detail: {en: 'Finnish, open 24/7.', bn: 'ফিনিশ ভাষায়, ২৪ ঘণ্টা খোলা।'}, tel: '+358925250111'},
      {name: 'MIELI Crisis Helpline (English)', detail: {en: 'Mon 4–8 pm, Thu–Fri 9 am – 1 pm.', bn: 'ইংরেজিতে: সোম বিকেল ৪–৮টা, বৃহস্পতি–শুক্র সকাল ৯টা – দুপুর ১টা।'}, tel: '+358925250116'},
      {name: 'Tukinet (English chat)', detail: {en: 'Crisis chat and email support in English.', bn: 'ইংরেজিতে ক্রাইসিস চ্যাট ও ইমেইল সহায়তা।'}, url: 'https://tukinet.fi/en/teemat/support-in-english'},
    ],
  },
  intl: {
    title: {en: 'In danger right now', bn: 'এখনই বিপদে থাকলে'},
    body: {en: 'Call your local emergency number. Find a free, confidential helpline in your country below.',
      bn: 'আপনার দেশের জরুরি নম্বরে ফোন করুন। নিচে আপনার দেশের বিনামূল্যের গোপন হেল্পলাইন খুঁজুন।'},
    contacts: [
      {name: 'Find A Helpline', detail: {en: 'Free crisis lines in 130+ countries.', bn: '১৩০টিরও বেশি দেশের বিনামূল্যের হেল্পলাইন।'}, url: 'https://findahelpline.com'},
    ],
  },
};

const gambling: Record<HelpRegion, HelpSection> = {
  bd: {
    title: {en: 'Help with gambling', bn: 'জুয়া থেকে মুক্তির সাহায্য'},
    contacts: [
      {name: 'Find A Helpline — Bangladesh', detail: {en: 'Gambling and mental health support in Bangladesh.', bn: 'বাংলাদেশে জুয়া ও মানসিক স্বাস্থ্য সহায়তা।'}, url: 'https://findahelpline.com/countries/bd/topics/gambling'},
      {name: 'Gamblers Anonymous', detail: {en: 'Free peer meetings, online and in person.', bn: 'বিনামূল্যে সহযোগী সভা, অনলাইনে ও সরাসরি।'}, url: 'https://www.gamblersanonymous.org'},
    ],
  },
  fi: {
    title: {en: 'Help with gambling', bn: 'জুয়া থেকে মুক্তির সাহায্য'},
    contacts: [
      {name: 'Peluuri', detail: {en: 'Free, confidential gambling helpline, English if possible. Mon–Fri 12–6 pm. Can connect you to free treatment.',
        bn: 'বিনামূল্যের গোপন জুয়া হেল্পলাইন, সম্ভব হলে ইংরেজিতে। সোম–শুক্র দুপুর ১২টা – সন্ধ্যা ৬টা। বিনামূল্যে চিকিৎসার ব্যবস্থাও করে দিতে পারে।'}, tel: '+358800100101'},
      {name: 'Peluuri website', detail: {en: 'Chat Mon, Wed, Fri 12–3 pm; 24/7 peer support in OmaPeluuri.', bn: 'চ্যাট সোম, বুধ, শুক্র দুপুর ১২–৩টা; OmaPeluuri-তে ২৪ ঘণ্টা সহযোগী সহায়তা।'}, url: 'https://peluuri.fi/en'},
    ],
  },
  intl: {
    title: {en: 'Help with gambling', bn: 'জুয়া থেকে মুক্তির সাহায্য'},
    contacts: [
      {name: 'Find A Helpline — gambling', detail: {en: 'Gambling helplines by country.', bn: 'দেশ অনুযায়ী জুয়ার হেল্পলাইন।'}, url: 'https://findahelpline.com/topics/gambling'},
      {name: 'Gamblers Anonymous', detail: {en: 'Free peer meetings, online and in person.', bn: 'বিনামূল্যে সহযোগী সভা, অনলাইনে ও সরাসরি।'}, url: 'https://www.gamblersanonymous.org'},
    ],
  },
};

const debt: Record<HelpRegion, HelpSection | null> = {
  bd: null,
  fi: {
    title: {en: 'Help with debt', bn: 'ঋণের সাহায্য'},
    body: {en: 'Free advisers can deal with collection companies and Ulosotto with you, and help you apply for debt restructuring (velkajärjestely). You do not have to open the letters alone.',
      bn: 'বিনামূল্যের পরামর্শদাতারা আপনার সাথে কালেকশন কোম্পানি ও Ulosotto-র বিষয় দেখবেন এবং ঋণ পুনর্গঠনে (velkajärjestely) আবেদন করতে সাহায্য করবেন। চিঠিগুলো একা খুলতে হবে না।'},
    contacts: [
      {name: 'Financial and debt counselling', detail: {en: 'Free public service (talous- ja velkaneuvonta). Find your local counsellor.', bn: 'বিনামূল্যের সরকারি সেবা (talous- ja velkaneuvonta)। আপনার এলাকার পরামর্শদাতা খুঁজুন।'}, url: 'https://www.oikeuspalveluvirasto.fi/en/financial-and-debt-counselling/'},
      {name: 'Takuusäätiö (Guarantee Foundation)', detail: {en: 'Free debt advice by phone and chat, nationwide.', bn: 'ফোন ও চ্যাটে বিনামূল্যে ঋণ পরামর্শ, সারা দেশে।'}, url: 'https://www.takuusaatio.fi/en'},
    ],
  },
  intl: null,
};

export const steps: {en: string; bn: string}[] = [
  {en: 'Get treatment alongside blocking. Blocking tools help, but for most people they are not enough on their own.',
    bn: 'ব্লক করার পাশাপাশি চিকিৎসা নিন। ব্লক করা সাহায্য করে, কিন্তু বেশিরভাগ মানুষের জন্য শুধু এটুকু যথেষ্ট নয়।'},
  {en: 'Block gambling payments at your bank, and self-exclude from gambling companies (for example Veikkaus in Finland).',
    bn: 'ব্যাংকে জুয়ার পেমেন্ট বন্ধ করুন এবং জুয়ার কোম্পানিগুলো থেকে নিজেকে বাদ দিন (যেমন ফিনল্যান্ডে Veikkaus)।'},
  {en: 'Let someone you trust hold your money for a while — for example your salary into a joint account you cannot spend from alone.',
    bn: 'কিছুদিনের জন্য বিশ্বস্ত কাউকে টাকার দায়িত্ব দিন — যেমন বেতন এমন যৌথ অ্যাকাউন্টে যা থেকে একা খরচ করা যায় না।'},
  {en: 'Do not try to win back losses. Chasing is how the debt grows.',
    bn: 'হারানো টাকা জিতে ফেরত আনার চেষ্টা করবেন না। এভাবেই ঋণ বাড়ে।'},
  {en: 'Tell one person. Hiding it makes it heavier; a counsellor can help you plan how to say it.',
    bn: 'অন্তত একজনকে বলুন। লুকিয়ে রাখলে বোঝা আরও ভারী হয়; কীভাবে বলবেন তা একজন কাউন্সেলর ঠিক করতে সাহায্য করতে পারেন।'},
  {en: 'Ask before deadlines. Schools, landlords and creditors often agree to more time when you ask early.',
    bn: 'সময়সীমার আগেই জিজ্ঞেস করুন। আগে চাইলে স্কুল, বাড়িওয়ালা ও পাওনাদাররা প্রায়ই সময় বাড়িয়ে দেন।'},
];

export function sections(region: HelpRegion): HelpSection[] {
  return [crisis[region], gambling[region], debt[region]].filter((s): s is HelpSection => s !== null);
}
