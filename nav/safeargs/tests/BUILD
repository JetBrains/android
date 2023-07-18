load("//tools/base/bazel:maven.bzl", "maven_repository")

maven_repository(
    # Dependencies needed for on-disk test projects, e.g. Gradle tests
    name = "testdeps_repo",
    # keep sorted
    artifacts = [
        "@maven//:androidx.activity.activity_1.0.0",
        "@maven//:androidx.appcompat.appcompat_1.1.0",
        "@maven//:androidx.lifecycle.lifecycle-common_2.1.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.1.0",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.1.0",
        "@maven//:androidx.navigation.navigation-safe-args-gradle-plugin_2.3.1",
        "@maven//:androidx.navigation.navigation-safe-args-gradle-plugin_2.5.3",
        "@maven//:androidx.savedstate.savedstate_1.0.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
    ],
    visibility = [":__subpackages__"],
)

test_suite(
    name = "suite",
    tests = [
        "//tools/adt/idea/nav/safeargs/tests/common:intellij.android.nav.safeargs.tests.common_tests",
        "//tools/adt/idea/nav/safeargs/tests/k1:intellij.android.nav.safeargs.tests.k1_tests",
        "//tools/adt/idea/nav/safeargs/tests/k2:intellij.android.nav.safeargs.tests.k2_tests",
    ],
    visibility = ["//visibility:public"],
)
