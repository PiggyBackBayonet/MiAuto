import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import java.util.Properties
import java.io.FileInputStream
// 顶层 `java` 被 Gradle 扩展名遮蔽：java.util.zip / java.io 的全限定名无法解析，
// 必须显式 import 这些 JDK 类后用短名（否则末尾的 dex 注入任务编译不过）。
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.File

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    namespace = "com.andrerinas.openheadunit"

    buildFeatures {
        buildConfig = true
        aidl = true // needed for shizuku
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    val copyRootAssets = tasks.register<Copy>("copyRootAssets") {
        // CHANGELOG.md is the English default; the About screen picks a locale-specific
        // CHANGELOG-<lang>[-<COUNTRY>].md when one exists. CHANGELOG-UPSTREAM.md holds the
        // inherited DiAuto / Open Headunit history and is deliberately not shipped.
        from(
            "${project.rootDir}/CHANGELOG.md",
            "${project.rootDir}/CHANGELOG-zh.md",
            "${project.rootDir}/CHANGELOG-zh-TW.md",
            "${project.rootDir}/LICENSE"
        )
        into("${project.layout.buildDirectory.get().asFile}/generated/assets/root")
    }

    // Scan available locales at configuration time and store as BuildConfig field
    val resDir = file("src/main/res")
    val availableLocales = resDir.listFiles { file ->
        file.isDirectory && file.name.startsWith("values-") &&
        // Filter out non-language qualifiers (night mode, screen size, etc.)
        !file.name.contains("night") &&
        !file.name.contains("land") &&
        !file.name.contains("port") &&
        !file.name.matches(Regex("values-[whsml]\\d+.*")) &&
        !file.name.matches(Regex("values-v\\d+")) &&
        // Check that it contains strings.xml (actual translation)
        file.resolve("strings.xml").exists()
    }?.map { dir ->
        // Extract locale code from directory name (e.g., "values-es" -> "es", "values-pt-rBR" -> "pt-rBR")
        dir.name.removePrefix("values-")
    }?.sorted() ?: emptyList()

    println("Detected available locales: $availableLocales")

    sourceSets {
        getByName("main") {
            assets.srcDirs("${project.layout.buildDirectory.get().asFile}/generated/assets/root")
        }
    }

    tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
        dependsOn(copyRootAssets)
    }

    tasks.configureEach {
        if (name.contains("lint", ignoreCase = true)) {
            dependsOn(copyRootAssets)
        }
    }

    defaultConfig {
        // MiAuto installs as its own package so it can sit alongside DiAuto / DiPlay.
        applicationId = "com.miauto.app"
        minSdk = 16
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        setProperty("archivesBaseName", "${applicationId}_${versionName}")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true

        // Store available locales in BuildConfig for runtime access
        // This is scanned at build time from values-XX directories
        buildConfigField("String", "AVAILABLE_LOCALES", "\"${availableLocales.joinToString(",")}\"")

        // MiAuto replaces the phone protocol with ICCOA CarLink (Xiaomi CarWith), so the Android
        // Auto half of the codebase is dead weight at runtime. This flag lets the shared code —
        // chiefly AapService, which MiAuto keeps only as the foreground keep-alive — skip the
        // AA-only side effects: the car-mode UI switch, the media-key hijack, and the one that
        // actually breaks the link, grabbing the WiFi Direct group and local hotspot that the
        // ICCOA SDK needs for its own data channel.
        buildConfigField("boolean", "ICCOA_ONLY", "true")

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            minSdk = 21
        }
        create("github") {
            dimension = "distribution"
            // Default minSdk 16 from defaultConfig is used
        }
    }

    signingConfigs {
        getByName("debug") {
            // storeFile = file("../keystore.jkc")
            // storePassword = property("HEADUNIT_KEYSTORE_PASSWORD") as String
            // keyAlias = property("HEADUNIT_KEYSTORE_ALIAS") as String
            // keyPassword = property("HEADUNIT_KEYSTORE_PASSWORD") as String
        }
        create("release") {
            val defaultStoreFile = when {
                rootProject.file("headunit-release-key.jks").exists() -> rootProject.file("headunit-release-key.jks")
                file("../headunit-release-key.jks").exists() -> file("../headunit-release-key.jks")
                else -> null
            }
            if (defaultStoreFile != null) {
                storeFile = defaultStoreFile
            }
            keyAlias = "headunit-revived"

            val keyfile = rootProject.file("key.properties")
            val signingPropsFile = rootProject.file("secrets.properties")

            if (keyfile.exists()) {
                val keyprops = Properties()
                keyprops.load(FileInputStream(keyfile))

                // key.properties lives at the repo root, so resolve a relative storeFile
                // against the root project too — `file(...)` would resolve it against the
                // `app` module directory and silently produce an UNSIGNED release APK.
                if (keyprops.containsKey("storeFile")) {
                    val declared = keyprops.getProperty("storeFile")
                    val candidates = listOf(rootProject.file(declared), file(declared))
                    storeFile = candidates.firstOrNull { it.exists() } ?: candidates.first()
                }
                if (keyprops.containsKey("storePassword")) storePassword = keyprops.getProperty("storePassword")
                if (keyprops.containsKey("keyAlias")) keyAlias = keyprops.getProperty("keyAlias")
                if (keyprops.containsKey("keyPassword")) keyPassword = keyprops.getProperty("keyPassword")
            } else if (signingPropsFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(signingPropsFile))

                storePassword = props.getProperty("HEADUNIT_KEYSTORE_PASSWORD")
                keyPassword = props.getProperty("HEADUNIT_KEY_PASSWORD")
            } else {
                val envStorePass = System.getenv("HEADUNIT_KEYSTORE_PASSWORD") ?: (project.findProperty("HEADUNIT_KEYSTORE_PASSWORD") as? String)
                val envKeyPass = System.getenv("HEADUNIT_KEY_PASSWORD") ?: (project.findProperty("HEADUNIT_KEY_PASSWORD") as? String)
                if (envStorePass != null) storePassword = envStorePass
                if (envKeyPass != null) keyPassword = envKeyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-project.txt"
            )

            val relConfig = signingConfigs.getByName("release")
            if (relConfig.storeFile != null && relConfig.storeFile!!.exists()) {
                signingConfig = relConfig
            }
        }

        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    lint {
        abortOnError = false
        disable += "PackagedPrivateKey"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        (this as KotlinJvmOptions).let {
            it.jvmTarget = "1.8"
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                var outputFileName = "${variant.applicationId}_${variant.versionName}_debug.apk"
                if(variant.buildType.name == "release") {
                    outputFileName = "${variant.applicationId}_${variant.versionName}.apk"
                    output.outputFileName = outputFileName
                }
                output.outputFileName = outputFileName
            }
    }
}

// Static launcher shortcuts carry android:targetPackage as a *literal* string: manifest
// placeholders are not substituted outside AndroidManifest.xml, and @string/... cannot be
// used either because ShortcutParser resolves that attribute against the system Resources.
// A literal that no longer matches the variant's applicationId compiles and packages
// perfectly well, yet every long-press menu entry silently does nothing at runtime
// ("ActivityNotFoundException: Shortcut could not be started"). Fail the build on drift.
androidComponents {
    onVariants { variant ->
        if (variant.buildType != "debug") return@onVariants
        val shortcuts = file("src/debug/res/xml/shortcuts.xml")
        if (!shortcuts.exists()) return@onVariants
        val expected = variant.applicationId.get()
        val mismatched = Regex("android:targetPackage=\"([^\"]+)\"")
            .findAll(shortcuts.readText())
            .map { it.groupValues[1] }
            .filter { it != expected }
            .toSet()
        if (mismatched.isNotEmpty()) {
            throw GradleException(
                "src/debug/res/xml/shortcuts.xml targets $mismatched but the " +
                    "${variant.name} applicationId is \"$expected\". Static shortcuts " +
                    "would launch nothing; update the android:targetPackage literals."
            )
        }
    }
}

dependencies {
    // ★ ICCOA CarLink 车机端 SDK（小米 CarWith）分两步引入：
    //   编译期用 stubs/icar-sdk-stubs.jar（纯签名存根，compileOnly 不进 APK）；
    //   运行期把 SDK dex 作为 classesN.dex 追加进 APK —— 见文件末尾 injectIccoaDex 任务。
    //   AGP 不消费 .dex 依赖，故不能写成 implementation(files("libs/icar-sdk.dex"))。
    compileOnly(files("stubs/icar-sdk-stubs.jar"))

    // ★ ICCOA SDK 运行期依赖：SDK 内部用 Room 维护「信任设备(Peer)」表与跨进程失效。
    //   已用 dex 解析器确认注入的 icar-sdk.dex 里 androidx/room/* 的【定义数 = 0】（只有引用），
    //   缺这个依赖会在 SDK 首次访问 Peer 库时抛 NoClassDefFoundError，使 init/广播链路中断。
    //   2.4.3 与已验证的旧 poc 工程一致，且本机 Gradle 缓存已有（可离线构建）。
    implementation("androidx.room:room-runtime:2.4.3")

    // Conscrypt (Flavor specific: 2.6.1 for Playstore 16KB alignment; 2.5.3 for Github minSdk 16)
    "playstoreImplementation"("org.conscrypt:conscrypt-android:2.6.1")
    "githubImplementation"("org.conscrypt:conscrypt-android:2.5.3")

    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    kapt("androidx.lifecycle:lifecycle-compiler:2.6.2")

    // KTX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
    implementation(project(":contract"))

    // Multidex
    implementation("androidx.multidex:multidex:2.0.1")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")

    // DexMaker for runtime subclassing (Hotspot Fix)
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")

    // Glide for image/GIF loading (custom loading screen)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Shizuku for root / shell access
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}

// =====================================================================
// ICCOA CarLink SDK dex 注入 + 重签（让 assemble*Debug / assemble*Release 直接产出完整可装 APK）
//
//   AGP 不消费 .dex 文件依赖（implementation/runtimeOnly files("*.dex") 都不会进 APK），
//   所以打包完成后把 SDK dex 作为下一个 classesN.dex 追加进 APK，再 zipalign + 重签。
//   debug 变体沿用默认 debug keystore；release 变体用 key.properties 里的发布密钥
//   （改 zip 会作废 AGP 的签名，所以两者都必须重签）。
//   纯 java.util.zip 实现，不依赖外部脚本。
// =====================================================================
val sdkDex = file("libs/icar-sdk.dex")
val sdkDexBytes: ByteArray = if (sdkDex.exists()) sdkDex.readBytes() else ByteArray(0)
// SDK 位置：ANDROID_HOME / ANDROID_SDK_ROOT → local.properties 的 sdk.dir → 旧的硬编码兜底。
// ★ 原先只认环境变量，读不到就掉到作者机器上的 C:/Users/Administrator/... —— 于是本机
//   （SDK 装在工程目录、只在 local.properties 里声明）构建时 zipalign/apksigner 全都找不到。
val iccoaSdkHome = (System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT"))
    ?.let { file(it) }
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { lp ->
        Properties().apply { FileInputStream(lp).use { s -> load(s) } }
            .getProperty("sdk.dir")?.let { file(it) }
    }
    ?: file("C:/Users/Administrator/AppData/Local/Android/Sdk")
val iccoaBuildToolsDir = iccoaSdkHome.resolve("build-tools").listFiles()
    ?.filter { it.isDirectory }
    // ★ Windows 上 zipalign 叫 zipalign.exe：只探测无扩展名的 "zipalign" 会一个都不匹配，
    //   于是静默掉到不存在的 build-tools/34.0.0，最后 exec 抛"找不到 zipalign"。
    ?.filter {
        (it.resolve("zipalign").exists() || it.resolve("zipalign.exe").exists()) &&
            it.resolve("lib/apksigner.jar").exists()
    }
    ?.maxByOrNull { it.name }
    ?: iccoaSdkHome.resolve("build-tools/34.0.0")
val iccoaZipalign = listOf("zipalign", "zipalign.exe")
    .map { iccoaBuildToolsDir.resolve(it) }
    .firstOrNull { it.exists() }
    ?: iccoaBuildToolsDir.resolve("zipalign")
val iccoaApksignerJar = iccoaBuildToolsDir.resolve("lib/apksigner.jar")
val iccoaDebugKeystore = file(System.getProperty("user.home")).resolve(".android/debug.keystore")

val injectIccoaDex = tasks.register("injectIccoaDex") {
    group = "build"
    description = "Appends the ICCOA CarLink SDK dex into the APKs and re-signs them"
    onlyIf { sdkDex.exists() }
    doLast {
        val apkRoot = layout.buildDirectory.dir("outputs/apk").get().asFile
        val apks = (apkRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            // debug *and* release: a release APK needs the SDK dex just as much, and
            // modifying the zip invalidates whatever AGP signed, so it is re-signed below.
            .flatMap { flavor -> (flavor.listFiles() ?: emptyArray()).filter {
                it.isDirectory && (it.name == "debug" || it.name == "release")
            } }
            .flatMap { buildTypeDir -> (buildTypeDir.listFiles() ?: emptyArray()).filter {
                it.isFile && it.name.endsWith(".apk") &&
                    !it.name.endsWith("-injected.apk") && !it.name.endsWith("-aligned.apk")
            } }
        require(apks.isNotEmpty()) { "未找到 APK：先运行 assembleGithubDebug / assembleGithubRelease" }

        // Signing material for the release variant is resolved here (rather than reusing
        // android.signingConfigs) so a missing/misconfigured key.properties degrades into a
        // loud warning instead of an exec() crash mid-build.
        val releaseKeyProps = rootProject.file("key.properties").takeIf { it.exists() }?.let {
            Properties().apply { FileInputStream(it).use { s -> load(s) } }
        }
        val releaseKeystore = releaseKeyProps?.getProperty("storeFile")?.let { declared ->
            listOf(rootProject.file(declared), file(declared)).firstOrNull { it.exists() }
        }

        apks.forEach { apk ->
            val isRelease = apk.parentFile?.name == "release"
            val base = apk.name.removeSuffix(".apk")
            val tmpInjected = File(apk.parentFile, "$base-injected.apk")
            val tmpAligned = File(apk.parentFile, "$base-aligned.apk")

            // --- 1. 追加 classesN.dex（保留原压缩方式 + 剥离 META-INF 签名）---
            val zin = ZipFile(apk)
            val entries = zin.entries().toList()

            // ★ 幂等保护：这是个 finalizer，每次 build 都会跑；若 APK 里已有历史注入副本
            //   （内容与 SDK dex 逐字节相同的 classesN.dex），必须先剔除，否则会再追加一份，
            //   APK 每 build 膨胀一次（曾出现 classes5 + classes6 两份 SDK）。
            val staleCopies = entries.filter { e ->
                e.name.matches(Regex("classes\\d+\\.dex")) &&
                    e.size == sdkDexBytes.size.toLong() &&
                    zin.getInputStream(e).readBytes().contentEquals(sdkDexBytes)
            }.map { it.name }.toSet()
            if (staleCopies.isNotEmpty()) {
                logger.lifecycle(">>> [${apk.name}] 移除历史注入副本: ${staleCopies.joinToString(", ")}")
            }
            val kept = entries.filter { it.name !in staleCopies }
            val existing = kept.map { it.name }.toSet()
            var n = 2
            while (existing.contains("classes$n.dex")) n++
            val newName = "classes$n.dex"
            BufferedOutputStream(FileOutputStream(tmpInjected)).use { bos ->
                val zout = ZipOutputStream(bos)
                for (e in kept) {
                    val fn = e.name
                    if (fn.startsWith("META-INF/") &&
                        (fn.endsWith(".RSA") || fn.endsWith(".SF") || fn.endsWith(".MF") ||
                         fn.endsWith(".DSA") || fn.endsWith(".EC"))) continue
                    val data = zin.getInputStream(e).readBytes()
                    val ne = ZipEntry(fn)
                    ne.method = e.method
                    ne.size = e.size
                    ne.crc = e.crc
                    ne.time = e.time
                    // STORED 必须 compressedSize == size；DEFLATED 不能预设（重压后大小会变）。
                    if (e.method == ZipEntry.STORED) ne.compressedSize = e.size
                    zout.putNextEntry(ne)
                    zout.write(data)
                }
                val de = ZipEntry(newName)
                de.method = ZipEntry.DEFLATED
                zout.putNextEntry(de)
                zout.write(sdkDexBytes)
                zout.closeEntry()
                zout.close()
            }
            zin.close()
            logger.lifecycle(">>> [${apk.name}] 已附加 $newName (${sdkDex.length()} B)")

            // --- 2. zipalign ---
            exec { commandLine(iccoaZipalign.absolutePath, "-p", "-f", "4", tmpInjected.absolutePath, tmpAligned.absolutePath) }

            // --- 3. 重签（release 用发布密钥，其余沿用默认 debug keystore）---
            require(iccoaApksignerJar.exists()) { "找不到 apksigner.jar：$iccoaApksignerJar" }
            val signArgs = if (isRelease) {
                if (releaseKeystore != null && releaseKeyProps != null) {
                    listOf(
                        "--ks", releaseKeystore.absolutePath,
                        "--ks-key-alias", releaseKeyProps.getProperty("keyAlias"),
                        "--ks-pass", "pass:" + releaseKeyProps.getProperty("storePassword"),
                        "--key-pass", "pass:" + releaseKeyProps.getProperty("keyPassword")
                    )
                } else {
                    logger.warn(">>> 未找到 key.properties/miauto-release-key.jks，${apk.name} 未重签（安装可能失败）")
                    null
                }
            } else if (iccoaDebugKeystore.exists()) {
                listOf(
                    "--ks", iccoaDebugKeystore.absolutePath,
                    "--ks-key-alias", "androiddebugkey",
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android"
                )
            } else {
                logger.warn(">>> 未找到 debug.keystore，${apk.name} 未重签（安装可能失败）")
                null
            }
            if (signArgs != null) {
                exec {
                    commandLine(
                        listOf("java", "-jar", iccoaApksignerJar.absolutePath, "sign") +
                            signArgs + listOf("--out", apk.absolutePath, tmpAligned.absolutePath)
                    )
                }
            }
            tmpInjected.delete()
            tmpAligned.delete()
            logger.lifecycle(">>> MiAuto APK 已注入 ICCOA SDK 并重签：${apk.absolutePath}")
        }
    }
}

// AGP 在配置后期才生成变体任务，tasks.named 不等待 → 放进 afterEvaluate 再挂 finalizedBy。
afterEvaluate {
    tasks.matching {
        it.name in setOf(
            "assembleGithubDebug", "assemblePlaystoreDebug",
            "assembleGithubRelease", "assemblePlaystoreRelease"
        )
    }.configureEach { finalizedBy(injectIccoaDex) }
}
