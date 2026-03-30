package xyz.bitsquidd

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
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