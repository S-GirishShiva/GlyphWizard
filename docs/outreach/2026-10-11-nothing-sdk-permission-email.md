# Email draft: Glyph Matrix SDK permission

**To:** GDKsupport@nothing.tech (the contact listed in the GlyphMatrix-Developer-Kit README; it doesn't name a licensing contact, so ask them to forward if needed)
**Subject:** Permission request: free, open-source app bundling the Glyph Matrix SDK

---

Hi Nothing Developer Programme team,

I'm building **GlyphWizard**, a free, non-commercial, open-source Android app for designing Glyph Matrix animations and assigning them to notifications and calls. I'm building it on my Phone (4a) Pro, and the source is at https://github.com/S-GirishShiva/GlyphWizard.

The app uses the Glyph Matrix SDK (`glyph-matrix-sdk-2.0.aar`) through the public APIs (`setAppMatrixFrame`, `closeAppMatrix`). I've read the Glyph SDK End User License Agreement and want to be sure I comply with it, specifically §2.1(c) (no redistribution) and §2.2 (no commercial use without permission).

What I'm doing today:
- The SDK binary is **not** committed to the repository. The build downloads it from your official GitHub repo at build time.
- Public releases are an SDK-free build (no LED output) until I hear from you.
- The app is and will stay free: no ads, no paid tiers, no in-app purchases.

My questions:
1. May I publish APKs (via GitHub Releases, installed with Obtainium) that **include** the Glyph Matrix SDK, as part of a free, non-commercial, open-source app?
2. If so, are there conditions you'd like me to follow (attribution, notices, a specific SDK version, or distribution channels)?
3. Is a GPL-3.0 licence for my own code, with an added permission for linking the Nothing SDK, acceptable to you?

Thanks for the SDK. The Glyph Matrix is a great canvas, and I'd like to build something the community enjoys.

Best regards,
Siddanth
[GitHub: S-GirishShiva]
