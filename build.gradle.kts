import java.util.jar.JarFile

plugins {
    `java-library`
    `maven-publish`
}

val sherpaVersion = providers.gradleProperty("sherpaVersion")
    .getOrElse(file("SHERPA_VERSION").readText().trim())
val buildSuffix = providers.gradleProperty("buildSuffix").get()
val sherpaSrc = layout.projectDirectory.dir("work/sherpa-onnx-$sherpaVersion")
val nativesDir = layout.projectDirectory.dir("natives")
val platforms = listOf("osx-aarch64", "osx-x64", "win-x64")

group = "ca.speechmetrics"
version = "$sherpaVersion-$buildSuffix"

// The Java API is compiled from the same sherpa-onnx tag as the natives.
// It still contains the TTS classes; without the native code they fail
// only if called.
sourceSets.main {
    java.srcDir(sherpaSrc.dir("sherpa-onnx/java-api/src/main/java"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("sherpa-onnx-jvm")
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.k2fsa.sherpa.onnx",
            "Implementation-Version" to project.version,
        )
    }
}

// Byte patterns that only appear when espeak-ng (or piper-phonemize,
// which wraps it) is linked in. A plain "espeak" search is not used: it
// matches "OfflineSpeakerDiarization".
val espeakPatterns = listOf("espeak_", "phonemize_eSpeak", "espeak-ng", "piper_phonemize", "piper-phonemize")

fun findPatterns(bytes: ByteArray): Set<String> {
    val text = String(bytes, Charsets.ISO_8859_1)
    return espeakPatterns.filter { text.contains(it) }.toSet()
}

val checkNoEspeak by tasks.registering {
    description = "Fails if any native library contains espeak-ng code."
    group = "verification"
    inputs.dir(nativesDir)
    doLast {
        val libs = nativesDir.asFile.walkTopDown().filter { it.isFile }.toList()
        if (libs.isEmpty()) {
            throw GradleException("No native libraries under ${nativesDir.asFile}")
        }
        val bad = libs.associateWith { findPatterns(it.readBytes()) }.filterValues { it.isNotEmpty() }
        libs.forEach { logger.lifecycle("checked ${it.relativeTo(nativesDir.asFile)}") }
        if (bad.isNotEmpty()) {
            throw GradleException(
                "espeak-ng code found (GPL): " +
                    bad.entries.joinToString { "${it.key.name} ${it.value}" },
            )
        }
    }
}

val nativeJars = platforms.associateWith { platform ->
    tasks.register<Jar>("nativeJar-$platform") {
        description = "Packages the $platform natives the way sherpa-onnx's LibraryUtils expects."
        group = "build"
        dependsOn(checkNoEspeak)
        archiveBaseName.set("sherpa-onnx-native-$platform")
        destinationDirectory.set(layout.buildDirectory.dir("native-jars"))
        from(nativesDir.dir(platform)) { into("sherpa-onnx/native/$platform") }
        doFirst {
            if (!nativesDir.dir(platform).asFile.isDirectory) {
                throw GradleException("Missing natives/$platform; run scripts/build-native.sh $platform")
            }
        }
    }
}

tasks.assemble { dependsOn(nativeJars.values) }

publishing {
    publications {
        create<MavenPublication>("jvm") {
            artifactId = "sherpa-onnx-jvm"
            from(components["java"])
        }
        platforms.forEach { platform ->
            create<MavenPublication>("native-$platform") {
                artifactId = "sherpa-onnx-native-$platform"
                artifact(nativeJars.getValue(platform))
            }
        }
        withType<MavenPublication>().configureEach {
            pom {
                description.set("sherpa-onnx $sherpaVersion built without text-to-speech (no espeak-ng)")
                url.set("https://github.com/k2-fsa/sherpa-onnx")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/speechmetrics/sherpa-onnx-build")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).getOrElse("")
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).getOrElse("")
            }
        }
    }
}

// Every publish re-runs the check, so nothing with espeak-ng can leave.
tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(checkNoEspeak) }
