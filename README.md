[readme_md (1).md](https://github.com/user-attachments/files/34434248/readme_md.1.md)
# 🇳🇱 Magister Van Gister

## 📖 Over Magister Van Gister (About)

**Magister Van Gister** (*"Magister van gisteren"*) is een gestroomlijnde Android-applicatie gebouwd met **Kotlin** en **Jetpack Compose**. De app maakt het voor Nederlandse leerlingen en docenten eenvoudig om oudere versies van de officiële Magister-app (zoals v2.7.0 t/m v2.9.2) op te halen, uit te pakken en direct te installeren.

In nieuwere updates van Magister worden soms handige functies of lay-outopties aangepast. Magister Van Gister helpt je om gemakkelijk terug te keren naar een vertrouwde, oudere versie.

## ✨ Kenmerken (Features)

* 📦 **Nieuwe & Oudere Versies Archief:** Automatisch overzicht van legacy builds van de [magister-apk-archive-releases](https://github.com/EpicMemazGuy/magister-apk-archive-releases) repository.

* ⚡ **Nieuwste `.xapk` en Split-APK Ondersteuning:** Ingebouwde `PackageInstaller` Session API-engine die `.xapk`-bestanden (split config APK's) automatisch in het geheugen uitpakt en direct aan Android aanbiedt. Geen externe apps (zoals SAI of App Manager) of root-toegang nodig!

* 🎨 **Material You & Dark Navy Aesthetics:** Volledige ondersteuning voor dynamische Material You (Material 3) kleurenschema's die zich aanpassen aan je systeemthema, gecombineerd met een strakke Dutch Navy & Electric Cyan accentstijl.

* 🛡️ **100% Privacy & F-Droid Compatible:** Geen propriëtaire Google Play Services, geen trackers, geen Firebase en geen reclame. Vrije open-source software (FOSS).

* 🔄 **Offline Fallback-catalogus:** Mocht de GitHub API de limiet bereiken, dan gebruikt de app een lokale fallback-catalogus voor snelle toegang.

## 📲 Installatie & Gebruik

### 1. Download de App

Download de nieuwste `.apk` via [Releases](../../releases) of via F-Droid zodra de build is goedgekeurd.

### 2. Geef Installatietoestemming

Bij het eerste gebruik zal de app vragen om de permissie **"Apps uit onbekende bronnen installeren"** (`REQUEST_INSTALL_PACKAGES`). Schakel dit eenmalig in.

### 3. Belangrijk bij Downgraden!

> ⚠️ **Let op:** Android staat het **niet** toe om een oudere app-versie direct over een nieuwere versie te installeren (`INSTALL_FAILED_VERSION_DOWNGRADE`).
>
> **Verwijder eerst de huidige Magister-app van je telefoon** voordat je een oudere versie installeert via Magister Van Gister!

## 🛠️ Gebouwd Met (Tech Stack)

* **Taal:** [Kotlin](https://kotlinlang.org/)

* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material You / Material 3)

* **Netwerk:** [OkHttp 4](https://square.github.io/okhttp/) & [Retrofit 2](https://square.github.io/retrofit/) voor GitHub API communicatie

* **Concurrentie:** Kotlin Coroutines & Flow

* **Installatie Engine:** Android `android.content.pm.PackageInstaller` Session API (`MODE_FULL_INSTALL`)

## 🚀 Zelf Bouwen (Building from Source)

Als je de app zelf wilt compileren met Android Studio of via de terminal:

```bash
# 1. Kloon de repository
git clone https://github.com/EpicMemazGuy/magister-van-gister.git
cd magister-van-gister

# 2. Bouw de debug APK
./gradlew assembleDebug

# 3. Installeer op aangesloten apparaat via ADB
./gradlew installDebug
```

## 🤝 Bijdragen (Contributing)

Bijdragen zijn van harte welkom!

* Wil je een bug melden of een functie voorstellen? Open een [Issue](../../issues).

* Wil je code bijdragen? Fork de repo, maak een feature-branch aan en stuur een **Pull Request**.

## 📜 Licentie (License)

Gelicentieerd onder de **GNU General Public License v3.0 (GPL-3.0)**. Zie het [LICENSE](LICENSE) bestand voor details.
