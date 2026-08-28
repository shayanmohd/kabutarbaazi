# Building KabutarBaazi

## Toolchain

There is no `java` on PATH on this machine. Every Gradle command needs JAVA_HOME set first:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Android Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`, JDK 21)
also works, but the project targets Java 17 so the Homebrew JDK matches what CI would use.

`org.gradle.java.home` is deliberately NOT set in `gradle.properties` because it is machine-specific.

## Gates

```bash
./gradlew :domain:test      # pure JVM, no emulator. The fast correctness gate.
./gradlew assembleDebug     # must stay green at every step.
```

## Secrets

`local.properties` is gitignored and holds:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon key>
MEDIA_BASE_URL=https://kb-media.kamapathy.app
```

The anon key is a public client key guarded by row-level security, not a secret. It stays out of
git so rotating a project does not require a code change. The `service_role` key must NEVER
appear in this file or anywhere in the app; it lives only in Supabase Edge Function secrets.

`keystore.properties` and `*.jks` are gitignored. The keystore password is printed exactly once
when the key is generated.

## First build

The first build downloads roughly 80 MB: nothing in the supabase-kt, ktor or coil trees is in
the local Gradle cache yet. Do it on a good connection.
