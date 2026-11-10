//        ItemKillCommand-Reloaded
//        Copyright (C) 2026  stone_block9965
//
//        Based on "kill Gobakubousi plugin" (ItemKillCommand)
//        Copyright (C) 2026  yukkurihimatubus
//
//        This program is free software: you can redistribute it and/or modify
//        it under the terms of the GNU General Public License as published by
//        the Free Software Foundation, either version 3 of the License, or
//        (at your option) any later version.
//
//        This program is distributed in the hope that it will be useful,
//        but WITHOUT ANY WARRANTY; without even the implied warranty of
//        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//        GNU General Public License for more details.
//
//        You should have received a copy of the GNU General Public License
//        along with this program.  If not, see https://www.gnu.org/licenses/.

plugins {
    java
}

group = "me.stone_block9965"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // Folia 26.3 向けにコンパイルする。
    // 使用する API (EntityScheduler / isOwnedByCurrentRegion など) は Paper 26.3 にも
    // 存在するため、ビルドされた jar は Paper 26.3 でもそのまま動作する。
    compileOnly("dev.folia:folia-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

// paper-plugin.yml の version: "${version}" をプロジェクトのバージョンに置換する
tasks.withType<ProcessResources>().configureEach {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("ItemKillCommand-Reloaded")
}
