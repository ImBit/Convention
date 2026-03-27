plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
}

group = "xyz.bitsquidd"

java {
    disableAutoTargetJvm()
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    jvmToolchain(21)
}


repositories {
    mavenCentral()
    gradlePluginPortal()
}

fun Provider<PluginDependency>.asDependency() =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }


dependencies {
    implementation(libs.plugins.shadow.asDependency())
    implementation(libs.plugins.kotlin.asDependency())
    implementation(libs.plugins.errorprone.asDependency())
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("bitConvention") {
            id = "xyz.bitsquidd.convention"
            implementationClass = "xyz.bitsquidd.BitConventionPlugin"
            displayName = "ImBit Convention Plugin"
            description = "Shared build conventions for all ImBit projects"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }
}