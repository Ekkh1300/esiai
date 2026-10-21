// Sokhan — open-source, provider-agnostic AI chat client for Android
// Settings file: plugin + dependency repositories ("Sokhan Project Settings").
//
// The standard repositories below (google(), mavenCentral(), gradlePluginPortal())
// are what the world wants. Machines behind geo-restricted networks (e.g. regions
// where dl.google.com is unavailable) can enable a mirror without touching this file:
// add `sokhan.mavenMirrorUrl=https://maven.aliyun.com/repository` to local.properties
// (the file is git-ignored). See README — "Building from source".

pluginManagement {
    repositories {
        val localProps = java.util.Properties()
        val lp = rootDir.resolve("local.properties")
        if (lp.exists()) lp.inputStream().use { localProps.load(it) }
        val mirror = localProps.getProperty("sokhan.mavenMirrorUrl")
        // Pinned artifacts pre-fetched for mirrors that fail on cold paths
        // (powered by tools/fetch-mirror-artifact.ps1). Always checked first.
        val seeded = rootDir.resolve("third_party/local-maven")
        if (seeded.resolve("com").exists()) {
            maven { name = "localSeed"; url = uri(seeded.toURI()) }
        }
        if (!mirror.isNullOrBlank()) {
            maven { url = uri("$mirror/google") }
            maven { url = uri("$mirror/public") }
            maven { url = uri("$mirror/gradle-plugin") }
            maven { name = "tencentCentral"; url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        }
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localProps = java.util.Properties()
        val lp = rootDir.resolve("local.properties")
        if (lp.exists()) lp.inputStream().use { localProps.load(it) }
        val mirror = localProps.getProperty("sokhan.mavenMirrorUrl")
        val seeded = rootDir.resolve("third_party/local-maven")
        if (seeded.resolve("com").exists()) {
            maven { name = "localSeed"; url = uri(seeded.toURI()) }
        }
        if (!mirror.isNullOrBlank()) {
            maven { url = uri("$mirror/google") }
            maven { url = uri("$mirror/public") }
            maven { name = "tencentCentral"; url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "Sokhan"
include(":app")
