# NetMon 🔍

Android network monitoring app — no root required.

Uses Android's `VpnService` API to capture all device traffic, parse packet headers in real-time, and show per-app network activity with DNS query logging.

## Features

- **No root required** — uses VpnService TUN interface
- **Per-app traffic breakdown** — see which apps connect where, how much data they send/receive
- **DNS query logging** — track every domain your apps resolve
- **Real-time dashboard** — Jetpack Compose UI with live-updating stats
- **Background monitoring** — runs as foreground service, survives app switching

## Architecture

- **Kotlin** + **Jetpack Compose** UI
- **VpnService** for packet interception
- **Raw ByteBuffer** parsing (no heavy dependencies like Netty/pcap4j)
- **Room** for persistent traffic logs
- **Hilt** for dependency injection
- **MVI** pattern with `StateFlow`

## Building

```bash
./gradlew assembleRelease
```

Requires Android SDK 34+ and JDK 17.

## CI

GitHub Actions builds release APKs on push to `main`. See `.github/workflows/build.yml`.

## License

MIT