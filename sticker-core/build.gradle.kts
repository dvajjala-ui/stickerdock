import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { id("org.jetbrains.kotlin.multiplatform") }

kotlin {
    jvmToolchain(17)
    // Android consumes the pure JVM artifact; no Android framework classes live
    // in this module. The same common sources build into Apple's framework.
    jvm()
    if (System.getProperty("os.name").startsWith("Mac")) {
        val framework = XCFramework("StickerDockCore")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "StickerDockCore"
                isStatic = true
                framework.add(this)
            }
        }
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(kotlin("test-junit")) }
    }
}
