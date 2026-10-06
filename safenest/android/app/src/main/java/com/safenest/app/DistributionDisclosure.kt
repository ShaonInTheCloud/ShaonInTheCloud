package com.safenest.app

fun appGuardDisclosure(language: String): String {
    if (LocalTestSession.enabled) return if (language == "bn")
        "আপনার সম্মতিতে সক্রিয় ৬০ মিনিটের পরীক্ষায় নির্বাচিত ব্লক অ্যাপ, SafeNest Test-এর Accessibility বন্ধ করার পৃষ্ঠা এবং SafeNest ও SafeNest Test নামে নির্দিষ্ট সিস্টেম সেটিংস পৃষ্ঠা (যেমন VPN নিয়ন্ত্রণ ও আনইনস্টল) থেকে হোমে ফেরাবে। 1.1.1.1-এর মতো পরিচিত VPN-এর সেটিংস ও সংযোগ ডায়ালগও শনাক্ত হলে হোমে ফেরাবে। মূল Settings, Accessibility তালিকা ও VPN তালিকা ব্যবহার করা যায়। গার্ড আগে নির্দিষ্ট পৃষ্ঠা থেকে পিছিয়ে তারপর হোমে ফেরায়, যাতে Settings আবার খুলতে পারেন। Stop test সব গার্ড বন্ধ করে; মেয়াদ শেষ বা ফোন চালু করলেও পরীক্ষা শেষ হয়। সীমিত লেবেল ফোনেই পরীক্ষা হয়; লেবেল বা পৃষ্ঠার বিষয়বস্তু সংরক্ষণ বা আপলোড হয় না। Accessibility সংযুক্ত থাকলে অল্প কয়েকটি উইন্ডোর ID ও অ্যাপের ID শুধু মেমরিতে থাকে। ব্রাউজারের বিষয়বস্তু, বার্তা বা পাসওয়ার্ড পড়ে না। এটি Device Owner অনুমতি নয়; শনাক্তকরণ ফোনভেদে ভিন্ন।"
    else
        "With your consent, the active 60-minute test returns Home from selected blocked apps, SafeNest Test Accessibility-disable detail, and selected system Settings pages named SafeNest or SafeNest Test, including VPN controls and removal screens. Recognized VPN detail/connection screens for known VPNs such as 1.1.1.1 also return Home. Main Settings, the Accessibility list and the VPN list stay usable. The guard leaves the protected detail first, then returns Home, so Settings can reopen normally. Stop test releases all guards; expiry or reboot ends the test. Narrow labels are checked locally; labels and page content are not saved or uploaded. A small window ID/package cache stays only in memory while Accessibility is connected. Browser pages, messages and passwords are not read. This is not Device Owner permission; recognition varies by phone."
    val play = !BuildConfig.ALLOW_SYSTEM_GUARD
    val en = if (play)
        "With your consent, SafeNest uses Accessibility to identify foreground app IDs and return Home from selected blocked apps and detected VPN apps during an active protection period. These observations stay on this device and are not recorded or uploaded. It does not read page bodies, messages, passwords, Settings controls or installer labels. Browsers remain open; website filtering uses the separate local DNS service. Android uninstall and permission controls remain available."
    else
        "With your consent, SafeNest checks foreground app IDs, supported browser address bars and narrow Settings or installer labels locally. During an active protection period, detected blocked apps, blocked addresses and matched SafeNest disconnect or uninstall controls return Home. Observations are not recorded or uploaded. Recognition varies by phone and does not guarantee impossible removal."
    val bn = if (play)
        "আপনার সম্মতিতে সক্রিয় সুরক্ষার মেয়াদে Accessibility দিয়ে অ্যাপের ID শনাক্ত করে নির্বাচিত ব্লক অ্যাপ ও শনাক্ত VPN অ্যাপ থেকে হোমে ফেরাবে। তথ্য ফোনেই থাকে; সংরক্ষণ বা সার্ভারে পাঠানো হয় না। পৃষ্ঠার বিষয়বস্তু, বার্তা, পাসওয়ার্ড, সেটিংস বা ইনস্টলারের লেবেল পড়ে না। ব্রাউজার খোলা থাকে; স্থানীয় DNS সেবা ওয়েবসাইট ফিল্টার করে। Android-এর আনইনস্টল ও অনুমতির নিয়ন্ত্রণ ব্যবহার করা যায়।"
    else
        "আপনার সম্মতিতে অ্যাপের ID, সমর্থিত ব্রাউজারের ঠিকানা ও সীমিত সেটিংস বা ইনস্টলার লেবেল ফোনে পরীক্ষা হয়। সক্রিয় মেয়াদে শনাক্ত ব্লক অ্যাপ, ঠিকানা ও SafeNest বন্ধ বা আনইনস্টল স্ক্রিন থেকে হোমে ফেরাবে। তথ্য সংরক্ষণ বা আপলোড হয় না। শনাক্তকরণ ফোনভেদে ভিন্ন; সরানো অসম্ভবের প্রতিশ্রুতি নয়।"
    return if (language == "bn") bn else en
}
