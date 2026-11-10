// JDK 25 が無い環境でも Gradle が自動でツールチェインをダウンロードできるようにする
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ItemKillCommand-Reloaded"
