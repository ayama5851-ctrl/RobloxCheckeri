ROBLOX CHECKER ANDROID - SOURCE PROJECT

Project ini adalah aplikasi Android native. Request HTTP dijalankan oleh aplikasi Android,
bukan browser/WebView, jadi tidak terkena CORS browser.

CATATAN PENTING:
- Ini project sumber, bukan APK yang sudah jadi.
- Untuk membuat APK tanpa memasang Android Studio di HP:
  1. Buat repository GitHub baru.
  2. Upload seluruh isi folder project ini ke repository (branch main).
  3. Buka tab Actions, pilih workflow "Build Android APK", lalu tekan Run workflow.
  4. Setelah selesai, buka hasil run dan unduh artifact "RobloxChecker-debug-apk".
  5. Ekstrak ZIP artifact dan install app-debug.apk di Android.
- GitHub account diperlukan untuk proses build cloud.
- Aplikasi tidak meminta password, cookie, atau token Roblox.
- CORS browser dihindari, tetapi Roblox tetap dapat mengirim HTTP 429, 403, atau membatasi inventory.
- RAP dihitung dari recentAveragePrice collectible yang dikembalikan endpoint.
- Pemeriksaan Korblox/Headless/Bonafield hanya pencocokan nama item collectible; bukan bukti kepemilikan bundle lengkap.
- Versi awal membaca halaman pertama inventory (hingga 100 item), status PARTIAL bila ada halaman lanjutan.
- Gunakan hanya data profil/inventory yang memang publik dan boleh diakses.
