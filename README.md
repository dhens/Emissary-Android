# Emissary-Android
![emsisary_android_scaledown](https://github.com/dhens/Emissary-Android/assets/11623868/ef17368d-6d3e-45f3-bdae-0096816d00ab)


[Click here to quickly set up Drawbridge and Emissary](https://github.com/dhens/Drawbridge/wiki/Quick-Start-Up-Guide-%E2%80%90-Get-Drawbridge-and-Emissary-protecting-your-applications-%E2%80%90-v0.1.0%E2%80%90alpha)

The Android Emissary client: an agent used to connect to the [Drawbridge reverse proxy](https://github.com/dhens/Drawbridge) with an mTLS certificate to access resources beyond Drawbridge. 

Self-hosting is a nightmare. If you're naive, you blow a hole in your home router to allow access to whatever resource you want to have accessible via the internet. If you're *"smart"*, you let some other service handle the ingress for you, most likely allowing for traffic inspection and mad metadata slurp-age by said service. Even if there's none of that, it doesn't really feel like you're sticking it to the man when you have to rely on a service to keep your self-hosted applications secure.

Emissary and Drawbridge solve this problem. Add Emissary to as many of your machines as you want, expose the Drawbridge reverse proxy server with required authentication details, _instead_ of your insecure web application or "movie server", and bam: your service is only accessible from approved Emissary clients.

Emissary does this by connecting to the Drawbridge server, which the user specifies in the Emissary app. Drawbridge will respond with a list of required configuration details for the machine running Emissary, such as being an updated Windows 11 machine, matching a specific serial number, connecting from a specific IP range. 

Drawbridge will routinely check in on Emissary clients to ensure they continue to match the required configuration. If an Emissary client fails to meet the Drawbridge Policy standards, Drawbridge will revoke the mTLS certificate, shutting off access to your resources beyond it. 

[Click here to read more about how Drawbridge works](https://github.com/dhens/Drawbridge).
