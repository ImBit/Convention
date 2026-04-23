package xyz.bitsquidd.util

enum class StandardDependencyConfig(val value: String) {
    IMPLEMENTATION("implementation"),
    API("api"),
    COMPILE_ONLY("compileOnly"),
    RUNTIME_ONLY("runtimeOnly"),
    TEST_IMPLEMENTATION("testImplementation"),
    TEST_RUNTIME_ONLY("testRuntimeOnly")
}

enum class BuildStrategy(val value: String) {
    DEFAULT("DEFAULT"),
    SPECIFIC("SPECIFIC"),
    NONE("NONE")
}

enum class CustomDependencyConfig(val value: String) {
    ERROR_PRONE("errorprone"),
}

sealed class ProjectProperty<T>(val value: String, val default: T) {
    object CustomJarName : ProjectProperty<String>("bit_customJarName", "")
    object DoShading : ProjectProperty<Boolean>("bit_doShading", true)
    object ShadeWhitelist : ProjectProperty<String>("bit_shadeWhitelist", "")
    object NullawayDirectory : ProjectProperty<String>("nullaway.annotatedPackages", "")
}