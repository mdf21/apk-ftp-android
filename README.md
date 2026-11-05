# FTP Manager - Aplikasi Android FTP (Client & Server)

Aplikasi Android modern berbasis **Native Kotlin** dan **Jetpack Compose (Material 3)** untuk mengelola dan mentransfer file menggunakan protokol **FTP (File Transfer Protocol)**.

Aplikasi ini menggabungkan 2 fungsi utama dalam satu aplikasi:
1. **FTP Server (Host di Android)**: Menjadikan HP Android Anda sebagai server FTP. Laptop/PC (Windows, macOS, Linux) dapat langsung membuka, mengunduh, dan mengunggah file di HP melalui Wi-Fi tanpa perlu kabel data USB.
2. **FTP Client (Akses Server Eksternal)**: Menghubungkan HP Android Anda ke server FTP eksternal (komputer lain, hosting, VPS, NAS, atau Raspberry Pi) untuk menjelajahi folder, mengunduh file, mengunggah file, membuat folder, mengganti nama, dan menghapus file.

---

## 🌟 Fitur Utama

### 📡 1. Mode FTP Server (HP sebagai Server)
* **Start / Stop Instan**: Cukup satu ketukan untuk mengaktifkan atau menonaktifkan server FTP.
* **Deteksi IP Wi-Fi Otomatis**: Menampilkan alamat lengkap (contoh: `ftp://192.168.1.15:2121`) dengan tombol *Salin Alamat*.
* **Background Foreground Service**: Server tetap berjalan stabil di latar belakang meskipun layar HP mati atau aplikasi diminimalkan, dilengkapi notifikasi kontrol cepat.
* **Autentikasi Aman / Fleksibel**:
  * Pilihan akses Anonim (bebas tanpa password) atau Username & Password khusus.
  * Mode Proteksi: *Read/Write* (baca & tulis) atau *Read-Only* (hanya unduh, mencegah perubahan data).
  * Port yang dapat disesuaikan (default: `2121`).
* **Live Connection Log**: Pemantauan langsung riwayat koneksi klien IP dan perintah transfer file yang sedang berlangsung.
* **Kompatibilitas Luas**: Dapat diakses langsung dari Windows File Explorer, macOS Finder, FileZilla, Cyberduck, browser, maupun terminal `curl`/`ftp`.

### 📂 2. Mode FTP Client (Akses Server dari HP)
* **Manajemen Profil Server**: Simpan banyak koneksi server FTP (nama label, host/IP, port, kredensial, mode pasif).
* **Penjelajah File Lengkap (File Explorer)**:
  * Navigasi folder bertingkat dengan tampilan breadcrumbs path.
  * Ikon tipe file cerdas (Gambar, Video, Musik, Arsip ZIP, Dokumen).
  * Menampilkan ukuran file dan tanggal pembaruan terakhir.
  * Fitur Pencarian / Filter nama file secara instan.
* **Operasi File Lengkap**:
  * **Download File**: Menyimpan file dari server FTP langsung ke folder `Download/FTP_Downloads` di HP dengan progress bar interaktif.
  * **Upload File**: Mengunggah foto, dokumen, atau video apa pun dari HP ke server FTP menggunakan pemilih berkas sistem Android (SAF).
  * **Buat Folder Baru (MKD)**.
  * **Ganti Nama File / Folder (RNFR/RNTO)**.
  * **Hapus File / Folder (DELE/RMD)**.
  * **Informasi Rinci File** (Path, Ukuran byte presisi, Tanggal, Hak akses/Permissions).

---

## 🛠️ Spesifikasi & Teknologi

* **Bahasa**: Kotlin 2.0.21
* **UI Toolkit**: Jetpack Compose dengan Material Design 3 (Material3)
* **Arsitektur**: MVVM (Model-View-ViewModel) + Kotlin Coroutines & StateFlow
* **Library FTP**:
  * Klien: `commons-net:commons-net:3.11.1` (Apache Commons Net)
  * Server: Embedded RFC 959 Socket Server ringan dan cepat berbasis Java/Kotlin Sockets tanpa dependensi berat.
* **Target OS**: Android 8.0 (API Level 26) hingga Android 15+ (API Level 35)

---

## 📁 Struktur Direktori Proyek

```
apk ftp android/
├── app/
│   ├── build.gradle.kts                # Konfigurasi dependensi modul aplikasi
│   ├── src/main/
│   │   ├── AndroidManifest.xml         # Izin jaringan, storage, dan layanan latar belakang
│   │   ├── java/com/edukasi/ftpmanager/
│   │   │   ├── FtpApplication.kt       # Inisialisasi kanal notifikasi
│   │   │   ├── MainActivity.kt         # Activity utama Jetpack Compose
│   │   │   ├── data/
│   │   │   │   ├── model/              # Data class (FtpFileItem, FtpServerProfile, dll)
│   │   │   │   ├── preferences/        # Penyimpanan profil server via SharedPreferences
│   │   │   │   ├── server/             # Implementasi Server FTP (SimpleFtpServer, FtpSessionHandler, Service)
│   │   │   │   └── client/             # Implementasi Klien FTP (FtpClientManager)
│   │   │   └── ui/
│   │   │       ├── theme/              # Warna, Tipografi, dan Tema Material 3
│   │   │       ├── components/         # Komponen reusable (FileItemRow, Dialogs)
│   │   │       └── screens/
│   │   │           ├── MainScreen.kt   # Navigasi tab utama
│   │   │           ├── server/         # Tampilan antarmuka Server FTP
│   │   │           └── client/         # Tampilan daftar profil & File Explorer FTP
│   │   └── res/                        # Resource XML, String, Warna, dan Ikon
├── gradle/
│   ├── libs.versions.toml              # Version catalog dependensi modern
│   └── wrapper/                        # Gradle wrapper properties
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Pengaturan modul & repository
└── README.md
```

---

## 🚀 Cara Membuka & Menjalankan di Android Studio

1. **Buka Android Studio** (versi Flamingo, Hedgehog, Iguana, Jellyfish, Koala, Ladybug, atau yang lebih baru).
2. Pilih menu **File** > **Open...** lalu arahkan ke folder ini:
   ```
   g:\3 PROGRAM DAN APLIKASI EDUKASI\apk ftp android
   ```
3. Tunggu hingga proses **Gradle Sync** selesai mengunduh dependensi secara otomatis.
4. Hubungkan smartphone Android Anda menggunakan kabel USB (aktifkan *USB Debugging*) atau jalankan Android Emulator.
5. Tekan tombol **Run** (ikon segitiga hijau `▶`) atau kombinasi tombol `Shift + F10`.

### 📦 Cara Menghasilkan File APK Mandiri (.apk)
Jika Anda ingin memasang langsung aplikasinya ke HP lain tanpa Android Studio:
1. Di Android Studio, klik menu **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
2. Tunggu proses kompilasi selesai.
3. Klik tautan **locate** pada notifikasi di pojok kanan bawah, file APK siap dipasang (`app-debug.apk`).

---

## 📖 Panduan Penggunaan

### A. Cara Menghubungkan Laptop ke HP (Mode Server):
1. Pastikan Laptop dan HP Android terhubung pada jaringan **Wi-Fi yang sama** (atau HP menyalakan Personal Hotspot dan Laptop terhubung ke hotspot tersebut).
2. Di aplikasi, buka tab **FTP Server (Host)** lalu tekan tombol **Mulai Server FTP**.
3. Buka **File Explorer** di Windows (atau Finder di Mac).
4. Pada kolom alamat atas (Address Bar), ketik alamat yang tertera di aplikasi, misalnya:
   ```
   ftp://192.168.1.15:2121
   ```
5. Tekan **Enter**. Jika muncul jendela login, masukkan:
   * **Username**: `admin`
   * **Password**: `123456`
   *(Pengaturan username & password bisa diubah sesuka Anda di menu "Ubah" pada kartu Pengaturan Server).*
6. Seluruh folder memori internal HP Anda akan terbuka di layar laptop. Anda bisa copy-paste file bolak-balik dengan kecepatan transfer Wi-Fi penuh!

---

### B. Cara Mengakses Server FTP Lain dari HP (Mode Klien):
1. Buka tab **FTP Client (Akses)**.
2. Tekan tombol **+ (Tambah Server Baru)**.
3. Masukkan rincian koneksi:
   * **Nama**: Label pengingat (misal: "Server PC", "Hosting Web")
   * **Host**: Alamat IP server tujuan (misal: `192.168.1.50` atau domain)
   * **Port**: Port FTP tujuan (default `21`)
   * **Username & Password**: Kredensial akun FTP
4. Klik **Simpan**, lalu tekan tombol **Hubungkan**.
5. File explorer akan terbuka:
   * Ketuk folder untuk masuk.
   * Tekan tombol **(+)** untuk **Upload File** atau **Buat Folder**.
   * Ketuk ikon titik tiga pada file untuk **Download**, **Ganti Nama**, atau **Hapus**.
   * File yang diunduh tersimpan otomatis di: `Penyimpanan Internal > Download > FTP_Downloads`.

