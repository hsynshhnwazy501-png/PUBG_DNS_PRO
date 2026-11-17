# ساخت APK در گوشی اندرویدی

برای ساخت APK بدون کامپیوتر، از AndroidIDE یا محیطی که Android SDK و Gradle را پشتیبانی می‌کند استفاده کنید:

1. پوشه پروژه را از ZIP خارج کنید.
2. پوشه `PUBG_DNS_PRO_Android` را به محیط توسعه وارد کنید.
3. JDK 17 و Android SDK Platform 35 را نصب کنید.
4. Gradle Sync را اجرا کنید و اجازه دهید کتابخانه WireGuard از Maven Central دریافت شود.
5. دستور `gradle assembleDebug` را در ریشه پروژه اجرا کنید.
6. APK در مسیر `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.

اگر محیط موبایل امکان دانلود وابستگی‌ها یا اجرای Gradle را ندارد، از Workflow گیت‌هاب در فایل `.github/workflows/build-apk.yml` استفاده کنید.

حداقل سیستم‌عامل خود اپ Android 10 است. برای تست VPN، فایل `.conf` معتبر با Endpoint سرور آلمان لازم است و اندروید مجوز ایجاد VPN را درخواست می‌کند.
