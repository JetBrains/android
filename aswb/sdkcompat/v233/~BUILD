# Description: Indirections for SDK changes to the underlying platform library.

licenses(["notice"])

java_library(
    name = "v233",
    srcs = ["com/google/idea/sdkcompat/refactoring/rename/DelegatingRenamePsiElementProcessorBase.java"] + glob([
        "com/google/idea/sdkcompat/codeinsight/**",
        "com/google/idea/sdkcompat/editor/**",
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/fig/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/psi/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/ui/icons/**",
        "com/google/idea/sdkcompat/vcs/**",
        "com/google/idea/sdkcompat/cpp/**",
        "com/google/idea/sdkcompat/java/**",
        "com/google/idea/sdkcompat/kotlin/**",
    ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
    visibility = ["//tools/adt/idea/aswb/sdkcompat:__pkg__"],
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
