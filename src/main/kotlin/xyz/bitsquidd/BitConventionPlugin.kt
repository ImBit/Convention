package xyz.bitsquidd

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import xyz.bitsquidd.util.BitProjectProperty
import xyz.bitsquidd.util.CustomDependencyConfig
import xyz.bitsquidd.util.StandardDependencyConfig
import xyz.bitsquidd.util.Util.library
import xyz.bitsquidd.util.Util.libs
import xyz.bitsquidd.util.Util.plugin

class BitConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val libs = target.libs()

        // ALLPROJECTS - repos only, no catalog access
        target.allprojects {
            // Configure plugins first.
            configurePlugins(libs)
            configureStandardDependencies(libs)
            configureErrorProne()
        }

        // SUBPROJECTS - plugins, extensions, tasks no catalog access
        target.subprojects {
            afterEvaluate {
                group = target.group
                version = target.version
            }

            // Configure extensions, dependencies, and tasks.
            // Must be called AFTER plugins are applied above.
            configureExtensions()
            configureTasks()
            configureShadowJar(libs)
            configurePublishing()
        }

        // ROOT ONLY - directory standardisation aggregator
        with(BuildUtil) { target.registerStandardiseDirectories() }
        target.afterEvaluate {
            tasks.matching { it.name in listOf("compileJava", "compileKotlin") }.configureEach {
                dependsOn("standardiseDirectories")
            }
            // If this is the ROOT project, register the aggregator task that depends on ALL subproject standardiseDirectories tasks.
            tasks.register("standardiseAllDirectories") {
                group = "build"
                description = "Standardises directories for all subprojects."
                dependsOn(
                    target.subprojects
                        .filter { it.tasks.findByName("standardiseDirectories") != null }
                        .map { it.tasks.named("standardiseDirectories") }
                )
            }
        }
    }


    private fun Project.configurePlugins(libs: VersionCatalog) {
        pluginManager.apply("java-library")
        pluginManager.apply("maven-publish")
        pluginManager.apply(libs.plugin("shadow"))
        pluginManager.apply(libs.plugin("kotlin"))
        pluginManager.apply(libs.plugin("errorprone"))
    }


    private fun Project.configurePublishing() {
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        groupId = project.group.toString()
                        artifactId = project.name.lowercase()
                        version = project.version.toString()

                        artifact(tasks.named("shadowJar"))
                        artifact(tasks.named("sourcesJar"))
                        artifact(tasks.named("javadocJar"))
                    }
                }
            }
        }

        tasks.named<Javadoc>("javadoc") {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    private fun Project.configureTasks() {
        tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
        tasks.named<Jar>("jar") {
            archiveFileName.set(findProperty(BitProjectProperty.CUSTOM_JAR_NAME.value) as String? + ".jar")
            finalizedBy(tasks.named("shadowJar"))
        }
        tasks.named("assemble") { dependsOn(tasks.named("shadowJar")) }
    }


    private fun Project.configureExtensions() {
        extensions.configure<JavaPluginExtension> {
            disableAutoTargetJvm()
            withSourcesJar()
            withJavadocJar()
            toolchain.languageVersion.set(JavaLanguageVersion.of(BitVersions.JAVA))
        }

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(BitVersions.JAVA)
        }
    }


    private fun Project.configureErrorProne() {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.errorprone {
                enabled.set(true)
                disableWarningsInGeneratedCode.set(true)
                disableAllWarnings.set(true)
                errorproneArgs.addAll(
                    "-Xep:CollectionIncompatibleType:ERROR",
                    "-Xep:EqualsIncompatibleType:ERROR",
                    "-Xep:MissingOverride:ERROR",
                    "-Xep:SelfAssignment:ERROR",
                    "-Xep:StreamResourceLeak:ERROR",
                    "-Xep:CanonicalDuration:OFF",
                    "-Xep:InlineMeSuggester:OFF",
                    "-Xep:ImmutableEnumChecker:OFF"
                )
            }
        }
    }


    private fun Project.configureShadowJar(libs: VersionCatalog) {
        plugins.withId(libs.plugin("shadow")) {
            tasks.withType<ShadowJar>().configureEach {
                val customJarName = findProperty(BitProjectProperty.CUSTOM_JAR_NAME.value) as String?
                if (customJarName != null) archiveBaseName.set(customJarName)
                archiveVersion.set("")
                archiveClassifier.set("")

                val excludes = (findProperty(BitProjectProperty.SHADOW_EXCLUDES.value) as? String)?.split(",") ?: emptyList()
                excludes.forEach { exclude("group:$it") }

                manifest { attributes["Implementation-Version"] = version }
            }
        }
    }


    private fun Project.configureStandardDependencies(libs: VersionCatalog) {
        dependencies {
            add(CustomDependencyConfig.ERROR_PRONE.value, libs.library("errorprone"))
            add(StandardDependencyConfig.COMPILE_ONLY.value, libs.library("jb.annotations"))
        }
    }

}