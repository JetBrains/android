# Tests that the prebuilt Kotlin plugin is binary compatible with Studio.
# To suppress false positives, see kotlin_ignored_problems.txt
py_test(
    name = "kotlin_compatibility_test",
    size = "medium",
    srcs = ["kotlin_compatibility_test.py"],
    args = [
        "--verifier",
        "$(location //prebuilts/tools/common/intellij-plugin-verifier)",
        "--ide_zip",
        "$(location //prebuilts/studio/intellij-sdk:studio-sdk-full-linux.zip)",
        "--plugin",
        "prebuilts/tools/common/kotlin-plugin/Kotlin",
        "--java_runtime",
        "prebuilts/studio/jdk/jdk11/linux",
        "--ignored_problems",
        "$(location kotlin_ignored_problems.txt)",
    ],
    data = [
        "kotlin_ignored_problems.txt",
        "//prebuilts/studio/intellij-sdk:studio-sdk-full-linux.zip",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/jdk/jdk11:jdk11_runtime_linux_files",
        "//prebuilts/tools/common/intellij-plugin-verifier",
    ],
    python_version = "PY3",
    tags = [
        # No need to test on multiple platforms.
        "no_mac",
        "no_windows",
    ],
)
