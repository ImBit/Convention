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
import xyz.bitsquidd.util.ProjectProperty
import xyz.bitsquidd.util.BuildStrategy
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
            val strategy = findProperty(ProjectProperty.BUILD_STRATEGY.value) ?: BuildStrategy.NONE.value

            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        groupId = project.group.toString()
                        artifactId = project.name.lowercase()
                        version = project.version.toString()

                        if (strategy == BuildStrategy.NONE.value) {
                            from(components["java"])
                        } else {
                            artifact(tasks.named("shadowJar"))
                            artifact(tasks.named("sourcesJar"))
                            artifact(tasks.named("javadocJar"))
                        }
                    }
                }
            }
        }
    }

    private fun Project.configureTasks() {
        val strategy = findProperty(ProjectProperty.BUILD_STRATEGY.value) ?: BuildStrategy.NONE.value

        tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

        tasks.named<Jar>("jar") {
            val customName = findProperty(ProjectProperty.CUSTOM_JAR_NAME.value) as String?
            if (customName != null) archiveBaseName.set(customName)

            if (strategy != BuildStrategy.NONE.value) {
                finalizedBy(tasks.named("shadowJar"))
            }
        }

        tasks.named("assemble") {
            if (strategy != BuildStrategy.NONE.value) {
                dependsOn(tasks.named("shadowJar"))
            } else {
                dependsOn(tasks.named("jar"))
            }
        }

        tasks.named<Javadoc>("javadoc") {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
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
        val strategy = findProperty(ProjectProperty.BUILD_STRATEGY.value) ?: BuildStrategy.NONE.value
        if (strategy == BuildStrategy.NONE.value) return

        plugins.withId(libs.plugin("shadow")) {
            tasks.withType<ShadowJar>().configureEach {
                val customJarName = findProperty(ProjectProperty.CUSTOM_JAR_NAME.value) as String?
                if (customJarName != null) archiveBaseName.set(customJarName)
                archiveVersion.set("")
                archiveClassifier.set("")

                val excludes = (findProperty(ProjectProperty.SHADOW_EXCLUDES.value) as? String)?.split(",") ?: emptyList()
                excludes.forEach { exclude(it) }

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