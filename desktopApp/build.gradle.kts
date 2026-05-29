import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "io.github.miuzarte.fhradio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "io.github.miuzarte.fhradio"
            packageVersion = "0.1.0"

            windows.iconFile = project.file("resources/windows/icon.ico")
            linux.iconFile = project.file("resources/linux/icon.png")
        }

        buildTypes.release.proguard {
            version = libs.versions.proguard.get()
            configurationFiles.from(project.file("../compose-desktop-rules.pro"))
        }
    }
}