/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.util

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
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

fun DependencyHandlerScope.shade(target: Any, includeTransitive: Boolean = false) {
    when (target) {
        is ProjectDependency -> {
            if (includeTransitive) {
                add("shade_internal", project(mapOf("path" to target.path)))
            } else {
                add("shade_internal", project(mapOf("path" to target.path, "configuration" to "shadow")))
            }
        } else -> {
            val dep = add("shade_internal", target)
            if (!includeTransitive) (dep as? ModuleDependency)?.isTransitive = false
        }
    }
}

fun DependencyHandlerScope.includeLibrary(target: Any) {
    add("api", target)
    add("implementation", target)
}

fun DependencyHandlerScope.shadeLibrary(target: Any, includeTransitive: Boolean = false) {
    shade(target, includeTransitive)
    includeLibrary(target)
}

fun DependencyHandlerScope.shadeImplementation(target: Any, includeTransitive: Boolean = false) {
    shade(target, includeTransitive)
    add("implementation", target)
}