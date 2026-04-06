# Build Scripts

This directory contains helper scripts for building the ViewSync app securely.

## 🔒 Security Note

**Never commit API keys or secrets to version control!**

The project uses GitHub Secrets for CI/CD builds. For local development, use one of the methods below.

---

## Available Scripts

### `build-with-secret.sh`

Interactive build script that prompts for your YouTube API key, then cleans it up after the build.

**Usage:**

```bash
./scripts/build-with-secret.sh
```

**Features:**
- ✅ Prompts for API key securely (hidden input)
- ✅ Creates temporary `local.properties`
- ✅ Builds the debug APK
- ✅ **Automatically removes the API key after build**
- ✅ Can also use environment variable: `YOUTUBE_API_KEY="your_key" ./scripts/build-with-secret.sh`

---

## Alternative Build Methods

### Method 1: Manual local.properties (Persistent)

```bash
# Create once, reuse across builds
echo "youtube.api.key=YOUR_API_KEY" > local.properties
./gradlew assembleDebug
```

⚠️ **Warning**: The key stays in `local.properties` until you delete it.

### Method 2: Environment Variable (One-time)

```bash
# Set environment variable and build
YOUTUBE_API_KEY="your_key" ./gradlew assembleDebug
```

✅ Key is not stored on disk.

### Method 3: GitHub Secrets (CI/CD Only)

For automated builds via GitHub Actions, the workflow already uses:
```yaml
secrets.YOUTUBE_API_KEY
```

**To configure:**
1. Go to your GitHub repository
2. Settings → Secrets and variables → Actions
3. Add new secret: `YOUTUBE_API_KEY`
4. Value: Your actual YouTube Data API v3 key

---

## Verifying Your Build

After building, verify the APK was created:

```bash
ls -lh app/build/outputs/apk/debug/
```

Install on connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Troubleshooting

### Build fails with "YOUR_YOUTUBE_API_KEY" error

The build is using the placeholder key. Ensure:
- `local.properties` exists with valid key, OR
- `YOUTUBE_API_KEY` environment variable is set, OR
- Use the interactive script: `./scripts/build-with-secret.sh`

### Check which key is being used

```bash
# View current local.properties (if exists)
cat local.properties
```

⚠️ **Don't commit this file!**

---

## Best Practices

1. ✅ Use GitHub Secrets for CI/CD
2. ✅ Use `build-with-secret.sh` for local development
3. ✅ Never share or commit API keys
4. ✅ Rotate API keys periodically
5. ✅ Restrict API keys in Google Cloud Console (package name + SHA-1)
