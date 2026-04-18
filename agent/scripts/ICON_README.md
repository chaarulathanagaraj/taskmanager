# AIOS Monitor Icon Files

Place the following icon files in this directory for the MSI installer:

1. aios-icon.ico - Application icon (256x256, 128x128, 64x64, 48x48, 32x32, 16x16)
   Used for: Start menu, desktop shortcut, taskbar, system tray

2. Place in resources/ subdirectory:
   - banner.bmp - Installer banner (493 x 58 pixels)
   - dialog.bmp - Installer dialog background (493 x 312 pixels)
   - license.rtf - License text in RTF format

Icon Design Guidelines:

- Primary color: #2196F3 (Material Blue)
- Secondary color: #4CAF50 (Material Green)
- Background: Transparent or white
- Style: Modern flat design with monitoring/health theme
- Elements: Monitor, heartbeat, shield, or gear icons

You can generate these icons using:

- GIMP (free)
- Inkscape (free, for vector to ICO)
- IconWorkshop (commercial)
- Online converters like convertio.co

For development, Windows will use a default icon if these files are missing.
