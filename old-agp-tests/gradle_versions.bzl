"""Common versions of Gradle"""

GRADLE_LATEST = "LATEST"
GRADLE_6_5 = "6.5"

GRADLE_DISTRIBUTIONS = {
    GRADLE_LATEST: ["//tools/base/build-system:gradle-distrib"],
    GRADLE_6_5: ["//tools/base/build-system:gradle-distrib-6.5"],
}
