# Loyiha: Prava Test 2026 (1-ilova)

Haydovchilik guvohnomasi nazariy imtihoni mashqi: 20 savol, 25 daqiqa, A1 — 16, qolganlar — 18 to'g'ri.
Internetsiz, o'zbekcha (lotin) va ruscha.

## Texnologiyalar
Kotlin 2.0, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, Navigation Compose, AdMob, UMP.
minSdk 24, compileSdk/targetSdk 34, `gradle/libs.versions.toml`.

## Modullar
- `core-common`, `core-ui`, `core-data`, `core-ads` — PrayerTimes loyihasidan olingan umumiy modullar
  (`core-ui/components/Quiz.kt` — savol ekrani komponentlari: AnswerOption, QuizHeader, StatTile; Abituriyent ham ishlatadi)
- `app-prava` — ilovaning o'zi

## Kontent
- `app-prava/src/main/assets/questions.json` — savollar bazasi. Birinchi ishga tushishda (yoki `version` oshganda) Room'ga yuklanadi.
- Har bir savolda `source` majburiy. Raqobatchi ilova/saytlardan nusxalash MUMKIN EMAS — faqat rasmiy manbalar (YHXBB materiallari, YHQ matni), izohlar o'zimizniki.
- Hozir 40 ta NAMUNAVIY savol (2 bilet). Ular umumiy YHQ qoidalari asosida yozilgan va rasmiy matn bilan tekshirilishi kerak.
- `correct` — to'g'ri javob indeksi; ekrandagi tartib `ContentLoader.displayOrder` bilan savol id si bo'yicha barqaror aralashtiriladi.
- Rasmlar: `res/drawable/sign_*.xml` (vektor) yoki `assets/images/<nom>.webp`. JSON'dagi `image` — fayl nomi.
- `ContentValidator` testi (DomainTest) har bilet 20 ta savol, manba, javoblar soni (2–5) va rasmlarni tekshiradi.

## Qoidalar
- Matnlar `strings.xml` da: `values` = o'zbekcha (lotin), `values-ru` = ruscha.
- Faqat AdMob TEST ID lari; haqiqiy ID lar `local.properties` dan (faqat release).
- Reklama: banner — bosh sahifa, biletlar, mavzular, statistika, ro'yxatlar; savol ekranida YO'Q.
  Interstitial — natija ekraniga kirishda (≥3 daqiqa oraliq, sessiyada ≤3). Rewarded — xatolar tahlilida batafsil izoh. App open — kuniga 1.
- Har bir ViewModel va hisoblash funksiyasi uchun unit test; build va testlar o'tishi shart; har bosqich — alohida commit.

## Build (JDK 17)
```bash
JAVA_HOME="/c/Users/HP/.jdks/corretto-17.0.5" ./gradlew assembleDebug testDebugUnitTest
```

## Texnik topshiriq
To'liq TZ: docs/TZ.md
