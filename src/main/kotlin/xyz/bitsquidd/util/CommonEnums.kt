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

enum class ProjectProperty(val value: String) {
    CUSTOM_JAR_NAME("bit_customJarName"),
    JAVA_VERSION("bit_javaVersion"),
    NULLAWAY_DIRECTORY("nullaway.annotatedPackages"),
}