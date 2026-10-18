package com.safenest.app

/**
 * Help & support content, the same as the iPhone app (mobile/src/lib/help.ts). Numbers checked 2026-10-18 on
 * the services' own pages: Kaan Pete Roi, MIELI, Peluuri, Finnish debt advice. Re-check every few months.
 */
object HelpContent {
    data class Text2(val en: String, val bn: String) { fun of(lang: String) = if (lang == "bn") bn else en }
    data class Contact(val name: String, val detail: Text2, val tel: String? = null, val url: String? = null)
    data class Section(val title: Text2, val body: Text2? = null, val contacts: List<Contact>)

    val regions = listOf("bd" to Text2("Bangladesh", "বাংলাদেশ"), "fi" to Text2("Finland", "ফিনল্যান্ড"), "intl" to Text2("Other countries", "অন্যান্য দেশ"))

    private val danger = Text2("In danger right now", "এখনই বিপদে থাকলে")
    private val callNow = Text2("If you might hurt yourself, call now. You do not have to be sure it is an emergency.",
        "নিজের ক্ষতি করার চিন্তা এলে এখনই ফোন করুন। জরুরি কি না নিশ্চিত হওয়ার দরকার নেই।")
    private val gamblingTitle = Text2("Help with gambling", "জুয়া থেকে মুক্তির সাহায্য")
    private val ga = Contact("Gamblers Anonymous", Text2("Free peer meetings, online and in person.", "বিনামূল্যে সহযোগী সভা, অনলাইনে ও সরাসরি।"), url = "https://www.gamblersanonymous.org")

    fun sections(region: String): List<Section> = when (region) {
        "bd" -> listOf(
            Section(danger, callNow, listOf(
                Contact("999", Text2("National emergency — police, ambulance, fire. 24/7, free.", "জাতীয় জরুরি সেবা — পুলিশ, অ্যাম্বুলেন্স, ফায়ার। ২৪ ঘণ্টা, বিনামূল্যে।"), tel = "999"),
                Contact("Kaan Pete Roi", Text2("Emotional support and suicide prevention. Every day 3 pm – 3 am.", "মানসিক সহায়তা ও আত্মহত্যা প্রতিরোধ। প্রতিদিন বিকেল ৩টা – রাত ৩টা।"), tel = "+8809612119911"))),
            Section(gamblingTitle, null, listOf(
                Contact("Find A Helpline — Bangladesh", Text2("Gambling and mental health support in Bangladesh.", "বাংলাদেশে জুয়া ও মানসিক স্বাস্থ্য সহায়তা।"), url = "https://findahelpline.com/countries/bd/topics/gambling"), ga)))
        "fi" -> listOf(
            Section(danger, callNow, listOf(
                Contact("112", Text2("Emergency number. 24/7, free.", "জরুরি নম্বর। ২৪ ঘণ্টা, বিনামূল্যে।"), tel = "112"),
                Contact("MIELI Crisis Helpline", Text2("Finnish, open 24/7.", "ফিনিশ ভাষায়, ২৪ ঘণ্টা খোলা।"), tel = "+358925250111"),
                Contact("MIELI Crisis Helpline (English)", Text2("Mon 4–8 pm, Thu–Fri 9 am – 1 pm.", "ইংরেজিতে: সোম বিকেল ৪–৮টা, বৃহস্পতি–শুক্র সকাল ৯টা – দুপুর ১টা।"), tel = "+358925250116"),
                Contact("Tukinet (English chat)", Text2("Crisis chat and email support in English.", "ইংরেজিতে ক্রাইসিস চ্যাট ও ইমেইল সহায়তা।"), url = "https://tukinet.fi/en/teemat/support-in-english"))),
            Section(gamblingTitle, null, listOf(
                Contact("Peluuri", Text2("Free, confidential gambling helpline, English if possible. Mon–Fri 12–6 pm. Can connect you to free treatment.",
                    "বিনামূল্যের গোপন জুয়া হেল্পলাইন, সম্ভব হলে ইংরেজিতে। সোম–শুক্র দুপুর ১২টা – সন্ধ্যা ৬টা। বিনামূল্যে চিকিৎসার ব্যবস্থাও করে দিতে পারে।"), tel = "+358800100101"),
                Contact("Peluuri website", Text2("Chat Mon, Wed, Fri 12–3 pm; 24/7 peer support in OmaPeluuri.", "চ্যাট সোম, বুধ, শুক্র দুপুর ১২–৩টা; OmaPeluuri-তে ২৪ ঘণ্টা সহযোগী সহায়তা।"), url = "https://peluuri.fi/en"))),
            Section(Text2("Help with debt", "ঋণের সাহায্য"),
                Text2("Free advisers can deal with collection companies and Ulosotto with you, and help you apply for debt restructuring (velkajärjestely). You do not have to open the letters alone.",
                    "বিনামূল্যের পরামর্শদাতারা আপনার সাথে কালেকশন কোম্পানি ও Ulosotto-র বিষয় দেখবেন এবং ঋণ পুনর্গঠনে (velkajärjestely) আবেদন করতে সাহায্য করবেন। চিঠিগুলো একা খুলতে হবে না।"),
                listOf(
                    Contact("Financial and debt counselling", Text2("Free public service (talous- ja velkaneuvonta). Find your local counsellor.", "বিনামূল্যের সরকারি সেবা (talous- ja velkaneuvonta)। আপনার এলাকার পরামর্শদাতা খুঁজুন।"), url = "https://www.oikeuspalveluvirasto.fi/en/financial-and-debt-counselling/"),
                    Contact("Takuusäätiö (Guarantee Foundation)", Text2("Free debt advice by phone and chat, nationwide.", "ফোন ও চ্যাটে বিনামূল্যে ঋণ পরামর্শ, সারা দেশে।"), url = "https://www.takuusaatio.fi/en"))))
        else -> listOf(
            Section(danger, Text2("Call your local emergency number. Find a free, confidential helpline in your country below.",
                "আপনার দেশের জরুরি নম্বরে ফোন করুন। নিচে আপনার দেশের বিনামূল্যের গোপন হেল্পলাইন খুঁজুন।"), listOf(
                Contact("Find A Helpline", Text2("Free crisis lines in 130+ countries.", "১৩০টিরও বেশি দেশের বিনামূল্যের হেল্পলাইন।"), url = "https://findahelpline.com"))),
            Section(gamblingTitle, null, listOf(
                Contact("Find A Helpline — gambling", Text2("Gambling helplines by country.", "দেশ অনুযায়ী জুয়ার হেল্পলাইন।"), url = "https://findahelpline.com/topics/gambling"), ga)))
    }

    val steps = listOf(
        Text2("Get treatment alongside blocking. Blocking tools help, but for most people they are not enough on their own.", "ব্লক করার পাশাপাশি চিকিৎসা নিন। ব্লক করা সাহায্য করে, কিন্তু বেশিরভাগ মানুষের জন্য শুধু এটুকু যথেষ্ট নয়।"),
        Text2("Block gambling payments at your bank, and self-exclude from gambling companies (for example Veikkaus in Finland).", "ব্যাংকে জুয়ার পেমেন্ট বন্ধ করুন এবং জুয়ার কোম্পানিগুলো থেকে নিজেকে বাদ দিন (যেমন ফিনল্যান্ডে Veikkaus)।"),
        Text2("Let someone you trust hold your money for a while — for example your salary into a joint account you cannot spend from alone.", "কিছুদিনের জন্য বিশ্বস্ত কাউকে টাকার দায়িত্ব দিন — যেমন বেতন এমন যৌথ অ্যাকাউন্টে যা থেকে একা খরচ করা যায় না।"),
        Text2("Do not try to win back losses. Chasing is how the debt grows.", "হারানো টাকা জিতে ফেরত আনার চেষ্টা করবেন না। এভাবেই ঋণ বাড়ে।"),
        Text2("Tell one person. Hiding it makes it heavier; a counsellor can help you plan how to say it.", "অন্তত একজনকে বলুন। লুকিয়ে রাখলে বোঝা আরও ভারী হয়; কীভাবে বলবেন তা একজন কাউন্সেলর ঠিক করতে সাহায্য করতে পারেন।"),
        Text2("Ask before deadlines. Schools, landlords and creditors often agree to more time when you ask early.", "সময়সীমার আগেই জিজ্ঞেস করুন। আগে চাইলে স্কুল, বাড়িওয়ালা ও পাওনাদাররা প্রায়ই সময় বাড়িয়ে দেন।"))
}
