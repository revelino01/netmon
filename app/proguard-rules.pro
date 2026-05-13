# Add project specific ProGuard rules here.

# PacketParser - keep all parsing logic
-keep class com.netmon.core.PacketParser { *; }
-keep class com.netmon.core.ParsedPacket { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-dontwarn dagger.hilt.**

# VpnService
-keep class com.netmon.vpn.VpnCaptureService { *; }