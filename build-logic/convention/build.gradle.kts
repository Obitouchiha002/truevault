import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "com.truevault.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly: these plugins are on the build classpath of the consuming build, not shipped here.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "truevault.android.application"
            implementationClass = "com.truevault.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "truevault.android.application.compose"
            implementationClass = "com.truevault.buildlogic.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "truevault.android.library"
            implementationClass = "com.truevault.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "truevault.android.library.compose"
            implementationClass = "com.truevault.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "truevault.android.feature"
            implementationClass = "com.truevault.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "truevault.android.hilt"
            implementationClass = "com.truevault.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "truevault.android.room"
            implementationClass = "com.truevault.buildlogic.AndroidRoomConventionPlugin"
        }
    }
}
