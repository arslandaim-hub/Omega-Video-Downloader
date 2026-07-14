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
</p>


### Download

<p align="center">
<a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
  <img src="https://img.shields.io/badge/GET%20IT%20ON-GitHub-000000?style=for-the-badge&logo=github&logoColor=white" alt="Get it on GitHub">
</a><!--
<br><br>
<a href="https://f-droid.org/packages/com.arslandaim.omega-video-downloader/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="220" alt="Get it on F-Droid">
</a>
<br><br>
-->
  <!--
<a href="https://github.com/arslandaim-hub/omega-video-downloader/releases/latest">
  <img src="https://img.shields.io/github/downloads/arslandaim-hub/omega-video-downloader/total?style=for-the-badge&logo=github&label=GitHub%20Downloads" alt="GitHub Downloads">
</a>
-->
</p>

### Key Features

- Universal Platform Support — Download videos from YouTube, Facebook, and Instagram.
- High-Resolution Downloads — Choose from multiple video and audio quality options.
- Private Locker — Securely protect your downloaded videos and audio files.
- Audio Extraction — Download audio-only versions of supported videos.
- Advanced Download Manager — Download multiple files simultaneously with ease.
- Built-in Media Player — Supports gesture controls for brightness, volume, and seeking.
- Modern Glassmorphism UI — Clean, elegant, and intuitive Material Design interface.
- Privacy First — No account required, no advertisements, no tracking, and no data collection.



### Architecture

Most downloaders are DUMB—they just look at a website's text and search for anything ending in .mp4 and they fail.
O-V-D uses (Site-Specific Extractors) which is a much smarter logic:

#### WHAT "O-V-D" DOES:

- **Mimics a Browser**:
Instead of just looking at the page, it acts like a real person using a browser. It can solve the puzzles (signatures) that YouTube uses to block bots.

- **Accesses Hidden APIs:**
It doesn't just scrape the website you see; it talks to the hidden private APIs that the official apps use. This is how it gets high-quality 4K video when others only see 720p.

- **Dynamic Adaptation:**
There are over 1,000 specific scripts inside the app, each dedicated to a different website. If Instagram changes how its site works today, the library is updated with new logic specifically for Instagram's new layout.

- **JS Reverse Engineering:**
Many sites hide their video links behind complex JavaScript code. O-V-D's logic is capable of reading and reverse-engineering that code to find the secret link that simple regex downloaders will never see.

O-V-D uses 3 heavy tools yt-dlp, FFmpeg and Aria2c. All of these increase the size of apk file to over 200MBs. But, it's worth it!

**Note**: Must download cookies.txt file of any brower using Chrome Cookies Extractor Extension and import it in app's settings.
Core Components

### Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_homescreen.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_locker.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_settings.png" width="130">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/file_downloads.png" width="130">
</p>

**⚠️ Important**

Importing cookies.txt file in app settings makes app effectively bypass bot detection.
This helps prevent video extraction from being blocked by supported websites and significantly improves download reliability. Without imported cookies, some links may fail. 


  <!--
<br><br>
<a href="https://f-droid.org/packages/com.arslandaim.omega-video-downloader/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="220" alt="Get it on F-Droid">
</a>
  -->


