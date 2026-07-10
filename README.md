<table align="center">
  <tr>
    <td>
      <img src="fastlane/metadata/android/en-US/images/icon.png" alt="PlayTube Icon" width="50">
    </td><table align="center">
  <tr>
    <td>
      |
    </td>
    <td>
      <h1 style="margin: 0;">Omega Video Downloader</h1>
    </td>
  </tr>
</table><p align="center">
A fast, privacy-focused social media video and audio downloader for <strong>YouTube</strong>, <strong>Facebook</strong>, and <strong>Instagram</strong>.<br>
Built with a modern Material Design interface, completely ad-free, with no tracking or data collection.
</p>---

📥 Download**

<p align="center">
<a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
  <img src="https://img.shields.io/badge/GET%20IT%20ON-GitHub-000000?style=for-the-badge&logo=github&logoColor=white" alt="Get it on GitHub">
</a><!--
<br><br>
<a href="https://f-droid.org/packages/com.arslandaim.omega-video-downloader/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="220" alt="Get it on F-Droid">
</a>
--><br><br>

<a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
  <img src="https://img.shields.io/github/downloads/arslandaim-hub/omega-video-downloader/total?style=for-the-badge&logo=github&label=GitHub%20Downloads" alt="GitHub Downloads">
</a>
</p>---

**✨ Featuresf**

- Universal Platform Support — Download videos from YouTube, Facebook, and Instagram.
- High-Resolution Downloads — Choose from multiple video and audio quality options.
- Private Locker — Securely protect your downloaded videos and audio files.
- Audio Extraction — Download audio-only versions of supported videos.
- Advanced Download Manager — Download multiple files simultaneously with ease.
- Built-in Media Player — Supports gesture controls for brightness, volume, and seeking.
- Modern Glassmorphism UI — Clean, elegant, and intuitive Material Design interface.
- Privacy First — No account required, no advertisements, no tracking, and no data collection.

---

**🏗️ Architecture**

Omega Video Downloader is built on a robust download engine rather than simple webpage scraping. While many conventional downloaders rely on basic pattern matching to locate media files, Omega Video Downloader uses site-specific extractors that understand how each supported platform delivers its content.

Browser Emulation

The extractor engine behaves like a real web browser instead of simply reading a webpage's source. It can process platform-specific authentication mechanisms and dynamically generated signatures required by services such as YouTube.

Site-Specific Extractors

The application includes an extensive collection of dedicated extractors, each designed for a specific website. When a platform changes its internal structure or delivery mechanism, only the corresponding extractor needs to be updated, allowing the downloader to adapt quickly without affecting support for other services.

Private API Integration

Whenever supported, the extractor communicates with the same private APIs used by official applications instead of relying solely on visible webpage content. This enables access to higher-quality media streams and additional metadata that basic webpage scraping often cannot discover.

JavaScript Analysis

Many modern websites generate media URLs dynamically using complex JavaScript. Omega Video Downloader's extraction engine can analyze and reverse-engineer this logic to locate media sources that simple regular-expression or HTML-based downloaders are unable to detect.

Core Components

Omega Video Downloader is powered by three industry-standard open-source tools:

Component| Purpose
yt-dlp| Advanced media extraction engine with support for thousands of websites.
FFmpeg| Media processing, format conversion, merging, and audio extraction.
aria2c| High-performance multi-connection download manager for faster and more reliable downloads.

**«Note**

Bundling these powerful components increases the APK size to over 200 MB, but it enables significantly better compatibility, higher download success rates, improved media quality, and a far more reliable downloading experience.»

---

**📱 Screenshots**

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_homescreen.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_locker.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_settings.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_downloads.png" width="130">
</p>---

**⚠️ Important**

Before downloading content, export your browser cookies as a ".txt" file and import them using the Import Cookies option in Settings.

This helps prevent video extraction from being blocked by supported websites and significantly improves download reliability. Without imported cookies, fetching videos may fail for some content.
 


  <!--
<br><br>
<a href="https://f-droid.org/packages/com.arslandaim.omega-video-downloader/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="220" alt="Get it on F-Droid">
</a>
  -->


