plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("dev.zacsweers.metro")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.androidx.datastore.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.dev.zacsweers.metrox.viewmodel)
            implementation(libs.dev.zacsweers.metrox.viewmodel.compose)

            implementation(libs.orbit.core)
            implementation(libs.orbit.compose)
            implementation(libs.orbit.viewmodel)

            implementation(projects.core.model)
            implementation(projects.core.proxy)
            implementation(projects.core.storage)
            implementation(projects.core.tls)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.zxing.core)
        }
    }
}
