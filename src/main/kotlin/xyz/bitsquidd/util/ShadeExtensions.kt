/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.util

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.withType


fun Project.relocate(vararg pairs: Pair<String, String>) {
    tasks.withType<ShadowJar>().configureEach {
        pairs.forEach { (from, to) ->
            run {
                relocate("nonapi.$from", to)
                relocate(from, to)
            }
        }
    }
}

fun DependencyHandlerScope.shade(target: Any) {
    when (target) {
        is ProjectDependency -> {
            add("shade_internal", project(mapOf("path" to target.path, "configuration" to "shadow")))
        }
        else -> {
            add("shade_internal", target)
        }
    }
}

fun DependencyHandlerScope.includeLibrary(target: Any) {
    add("api", target)
    add("implementation", target)
}

fun DependencyHandlerScope.shadeApi(target: Any) {
    shade(target)
    includeLibrary(target)
}