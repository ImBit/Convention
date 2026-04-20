package xyz.bitsquidd

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import xyz.bitsquidd.BuildUtil.standardiseDirectories
import xyz.bitsquidd.util.CustomDependencyConfig
import xyz.bitsquidd.util.ProjectProperty
import xyz.bitsquidd.util.StandardDependencyConfig
import xyz.bitsquidd.util.Util.libs

class BitConventionPlugin : Plugin<Project> {
    companion object {
        private const val ERROR_PRONE = "com.google.errorprone:error_prone_core:2.48.0"
        private const val NULLAWAY = "com.uber.nullaway:nullaway:0.13.1"
        private const val JB_ANNOTATIONS = "org.jetbrains:annotations:26.1.0"

        private const val PLUGIN_SHADOW = "com.gradleup.shadow"
        private const val PLUGIN_ERRORPRONE = "net.ltgt.errorprone"
    }

    override fun apply(target: Project) {
        val libs = target.libs()

        // ALLPROJECTS - repos only
        target.allprojects {
            // Configure plugins first.
            configurePlugins()
            configureStandardDependencies()
            configureErrorProne()
            configureShadowJar()
        }

        // Configure extensions, dependencies, and tasks.
        // Must be called AFTER plugins are applied above.
        // SUBPROJECTS - plugins, extensions
        target.subprojects {
            configureExtensions()
            configureTasks()
            configurePublishing()
        }

        // ROOT ONLY - directory standardisation
        target.standardiseDirectories()
    }


    private fun Project.configurePlugins() {
        pluginManager.apply("java-library")
        pluginManager.apply("maven-publish")
//        pluginManager.apply(libs.plugin("kotlin"))
        pluginManager.apply(PLUGIN_SHADOW)
        pluginManager.apply(PLUGIN_ERRORPRONE)
    }


    private fun Project.configurePublishing() {
        afterEvaluate {
            if (group.toString().isBlank() || version.toString() == "unspecified") {
                throw IllegalStateException("Project group or version is not set.")
            }

            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        groupId = project.group.toString().lowercase()
                        artifactId = project.name.lowercase()
                        version = project.version.toString()

                        artifact(tasks.named("shadowJar"))
                        artifact(tasks.named("sourcesJar"))
                        artifact(tasks.named("javadocJar"))
                    }
                }
            }
        }
    }

    private fun Project.configureTasks() {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.add("-XDaddTypeAnnotationsToSymbol=true")
        }

        tasks.named<Javadoc>("javadoc") {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    private fun Project.configureExtensions() {
        if (extensions.findByType(JavaPluginExtension::class) != null) {
            extensions.configure<JavaPluginExtension> {
                disableAutoTargetJvm()
                withSourcesJar()
                withJavadocJar()
            }
        }
    }


    private fun Project.configureErrorProne() {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.errorprone {
                enabled.set(true)
                disableWarningsInGeneratedCode.set(true)
                disableAllWarnings.set(true)

                findProperty(ProjectProperty.NULLAWAY_DIRECTORY.value)?.let {
                    check("NullAway", CheckSeverity.ERROR)
                    option("NullAway:AnnotatedPackages", it as String)
                    option("NullAway:ExternalInitAnnotations", "org.jetbrains.annotations.NotNullByDefault")
                    option("NullAway:NonnullAnnotations", "org.jetbrains.annotations.NotNull")
                    option("NullAway:NullableAnnotations", "org.jetbrains.annotations.Nullable")
                }

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


    private fun Project.configureShadowJar() {
        val shade = configurations.maybeCreate("shade_internal")

        configurations.getByName("compileOnly").extendsFrom(shade)

        plugins.withId(PLUGIN_SHADOW) {
            val jarTask = tasks.named("jar")
            tasks.withType<ShadowJar>().configureEach {
                dependsOn(jarTask)
                configurations = listOf(project.configurations.getByName("shade_internal"))
                archiveVersion.set("")
                archiveClassifier.set("")
                manifest { attributes["Implementation-Version"] = version }

                findProperty(ProjectProperty.CUSTOM_JAR_NAME.value)?.let {
                    val customName = it as String
                    if (customName.isNotBlank()) archiveBaseName.set(customName)
                }
            }
        }
    }

    private fun Project.configureStandardDependencies() {
        dependencies {
            add(CustomDependencyConfig.ERROR_PRONE.value, ERROR_PRONE)
            add(CustomDependencyConfig.ERROR_PRONE.value, NULLAWAY)

            add(StandardDependencyConfig.COMPILE_ONLY.value, JB_ANNOTATIONS)
        }
    }

}