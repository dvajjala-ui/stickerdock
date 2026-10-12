# QA matrix — all unverified until run

| Test | Expected |
|---|---|
| Gradle build | :app:assembleDebug succeeds |
| Pack CRUD | Persists after restart |
| PNG/JPEG import | Alpha-preserving 512px WebP under 100KB |
| Duplicate import | Rejected inside pack, permitted across packs |
| Animated input | Rejected with clear message |
| Limit | No more than 30 stickers in pack |
| Rename/delete | DB, provider metadata and assets consistent |
| Copy/move | Files and DB remain consistent |
| Backup/restore | ZIP integrity, checksum, new IDs |
| ZIP traversal/bomb | Rejected |
| WhatsApp consumer | User-confirmed pack import |
| WhatsApp Business | User-confirmed pack import |
| In-app Switch Account | Determine whether second account can be targeted; expect limitation |
| Samsung Dual Messenger | Test with real device |
| Offline usage | Works in airplane mode |

Use JDK 17, Gradle 8.9, Android SDK35. Test on Galaxy S24 Ultra and emulator. Never mark as passing without evidence.
