package com.safenest.app

fun appGuardDisclosure(language: String): String {
    if (LocalTestSession.enabled) return if (language == "bn")
        "আপনার সম্মতিতে Accessibility দিয়ে নির্বাচিত অ্যাপ ব্লক ও SafeNest Test-এর VPN বন্ধ, Forget VPN, আনইনস্টল বা Force stop নিয়ন্ত্রণের স্ক্রিন শনাক্ত করে হোমে ফেরাবে। শনাক্ত স্ক্রিন খুললেই হোমে ফেরাতে পারে। শুধু সক্রিয় ৬০ মিনিটের পরীক্ষায় কাজ করে; অ্যাপের Stop test চাপলে বন্ধ হয়। সীমিত সেটিংস ও ইনস্টলার লেবেল ফোনেই পরীক্ষা হয়; সংরক্ষণ বা আপলোড হয় না। ব্রাউজারের বিষয়বস্তু, বার্তা বা পাসওয়ার্ড পড়ে না। এটি Device Owner অনুমতি নয়। অন্য অ্যাপ ও Accessibility অনুমতি বন্ধ করার স্ক্রিন ব্যবহার করা যায়।"
    else
        "With your consent, Accessibility returns Home from selected blocked apps and recognized SafeNest Test VPN disconnect, Forget VPN, Uninstall or Force stop screens. It may return Home as soon as a protected screen opens. This runs only during an active 60-minute test; Stop test in the app ends it. Narrow Settings and installer labels are checked on this phone and are not recorded or uploaded. Browser pages, messages and passwords are not read. This is not Device Owner permission. Other apps and Accessibility permission controls remain available."
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
