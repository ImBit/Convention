package xyz.bitsquidd

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.assembler.tasks.Assemble
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

                        from(components["java"])
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

                property(ProjectProperty.NullawayDirectory).takeIf { it.isNotBlank() }?.let {
                    check("NullAway", CheckSeverity.ERROR)
                    option("NullAway:AnnotatedPackages", it)
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
        val shadeTransitive = configurations.maybeCreate("shade_internal_transitive")
        shadeTransitive.isTransitive = true
        val shadeNonTransitive = configurations.maybeCreate("shade_internal")
        shadeNonTransitive.isTransitive = false

        configurations.getByName("implementation").extendsFrom(shadeTransitive, shadeNonTransitive)

        val doShading = property(ProjectProperty.DoShading)

        if (doShading) {
            tasks {
                named("assemble") { dependsOn("shadowJar") }
            }

            plugins.withId(PLUGIN_SHADOW) {
                tasks.withType<ShadowJar>().configureEach {
                    configurations = listOf(shadeTransitive, shadeNonTransitive)

                    archiveVersion.set("")
                    archiveClassifier.set("") //fat
                    manifest { attributes["Implementation-Version"] = version }

                    property(ProjectProperty.CustomJarName).let {
                        if (it.isNotBlank()) archiveBaseName.set(it)
                    }

                    dependencies {
                        val whitelist = property(ProjectProperty.ShadeWhitelist)
                            .split(',')
                            .map(String::trim)
                            .filter(String::isNotBlank)

                        if (whitelist.isNotEmpty()) {
                            exclude { dependency ->
                                !whitelist.any { dependency.moduleGroup?.startsWith(it) == true }
                            }
                        }
                    }
                }
            }
        }

        tasks.named<Jar>("jar") {
            archiveVersion.set("")
            archiveClassifier.set(if (doShading) "ignored" else "") // No classifier for the normal jar.
            manifest { attributes["Implementation-Version"] = version }

            property(ProjectProperty.CustomJarName).let {
                if (it.isNotBlank()) archiveBaseName.set(it)
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

    private fun <T> Project.property(prop: ProjectProperty<T>): T {
        val raw = findProperty(prop.value) ?: return prop.default
        @Suppress("UNCHECKED_CAST")
        return when (prop.default) {
            is Boolean -> (raw.toString().toBoolean()) as T
            is String -> raw.toString() as T
            else -> raw as T
        }
    }

}