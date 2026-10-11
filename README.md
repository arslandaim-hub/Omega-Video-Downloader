<div align="center">

<table align="center" style="border: none;">
  <tr>
    <td align="center" style="border: none; padding-right: 15px;">
      <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Omega Video Downloader Icon" width="80">
    </td>
    <td align="left" style="border: none;">
      <h1 style="margin: 0; font-size: 2.2rem;">Omega Video Downloader</h1>
    </td>
  </tr>
</table>

<p>
A fast, privacy-focused social media video and audio downloader for <strong>YouTube</strong>, <strong>Facebook</strong>, <strong>Instagram</strong> and <strong>Twitter</strong>.<br>
Material 3 Design, completely ad-free, with no tracking or data collection.
</p>

<!-- FEATURE GRAPHIC -->
<br>
<img src="fastlane/metadata/android/en-US/images/featureGraphic.png" alt="Omega Video Downloader Feature Graphic" width="100%">
<br>

<!-- STATUS BADGES -->
<p align="center">
  <a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
    <img src="https://img.shields.io/github/v/release/arslandaim-hub/omega-video-downloader?style=flat-square&color=10b981" alt="Latest Release">
  </a>
  <a href="https://github.com/arslandaim-hub/omega-video-downloader/stargazers">
    <img src="https://img.shields.io/github/stars/arslandaim-hub/omega-video-downloader?style=flat-square&color=fbbf24" alt="GitHub Stars">
  </a>
  <a href="https://github.com/arslandaim-hub/omega-video-downloader/network/members">
    <img src="https://img.shields.io/github/forks/arslandaim-hub/omega-video-downloader?style=flat-square&color=fbbf24" alt="GitHub Forks">
  </a>
  <a href="https://github.com/arslandaim-hub/omega-video-downloader/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/arslandaim-hub/omega-video-downloader?style=flat-square&color=3b82f6" alt="License">
  </a>
  <img src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Support">
  <img src="https://img.shields.io/badge/Kotlin-100%25-B125EA?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
</p>

<br>

<a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
  <img src="https://img.shields.io/badge/GET%20IT%20ON-GitHub-000000?style=for-the-badge&logo=github&logoColor=white" alt="Get it on GitHub">
</a>

</div>

---

### Key Features

- **Universal Platform Support** — Download videos from YouTube, Facebook, and Instagram and Twitter.
- **High-Resolution Downloads** — Choose from multiple video and audio quality options.
- **Private Locker** — Securely protect your downloaded videos and audio files.
- **Audio Extraction** — Download audio-only versions of supported videos.
- **Advanced Download Manager** — Download multiple files simultaneously with ease.
- **Built-In Media Player** — Supports gesture controls for brightness, volume, and seeking.
- **Modern Glassmorphism UI** — Clean, elegant, and intuitive Material Design interface.
- **Privacy First** — No account required, no advertisements, no tracking, and no data collection.

---

### Architecture

Most downloaders are DUMB—they just look at a website's text and search for anything ending in `.mp4` and they fail.
Omega Video Downloader uses **Site-Specific Extractors** which is a much smarter logic:

#### WHAT IT DOES:

* **Mimics a Browser**: Instead of just looking at the page, it acts like a real person using a browser. It can solve the puzzles (signatures) that YouTube uses to block bots.
* **Accesses Hidden APIs:** It doesn't just scrape the website you see; it talks to the hidden private APIs that the official apps use. This is how it gets high-quality 4K video when others only see 720p.
* **Dynamic Adaptation:** There are over 1,000 specific scripts inside the app, each dedicated to a different website. If Instagram changes how its site works today, the library is updated with new logic specifically for Instagram's new layout.
* **JS Reverse Engineering:** Many sites hide their video links behind complex JavaScript code. The app's logic is capable of reading and reverse-engineering that code to find the secret link that simple regex downloaders will never see.

> **Note:** The app utilizes 3 heavy-duty tools: **yt-dlp**, **FFmpeg**, and **Aria2c**. All of these increase the APK file size to over 100MB, but it's completely worth it for unmatched reliability!

** Important Setup Note:** 
You can add `cookies.txt` file from any browser using a Chrome Cookies Extractor Extension and import it in the app's settings. Importing cookies makes the app effectively bypass bot detection, preventing video extraction blocks and significantly improving download reliability. Without imported cookies, some links may fail.

---

### Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_homescreen.png" width="18%" alt="Home Screen">
  &nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_locker.png" width="18%" alt="Locker Screen">
  &nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_settings.png" width="18%" alt="Settings Screen">
  &nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_downloads.png" width="18%" alt="Downloads Screen">
</p>
