load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//src:__subpackages__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":app",
        ":app_srcs",
        #":app_test",
        #":app_test_bin",
        ":lib",
        ":resources",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
)

android_library(
    name = "app_srcs",
    srcs = glob(["app/java/**/*.java"]),
    exports_manifest = 1,
    javacopts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    manifest = "app/AndroidManifest.xml",
    resource_files = glob(["app/res/**"]),
    deps = [
        ":lib",
        ":resources",
        "//tools/adt/idea/aswb/aswb/testdata/projects/android1/external:lib",
    ],
)

android_binary(
    name = "app",
    manifest = "app/AndroidManifest.xml",
    deps = [
        ":app_srcs",
    ],
)

android_library(
    name = "lib",
    srcs = glob(["lib/java/**/*.java"]),
    custom_package = "com.example.bazel",
    manifest = "lib/AndroidManifest.xml",
    resource_files = glob(["lib/res/**"]),
)

android_library(
    name = "resources",
    srcs = [],
    manifest = "resources/AndroidManifest.xml",
    resource_files = glob(["resources/res/**"]),
)

# TODO(b/331378322) Re-enable this.
#android_application_test(
#    name = "app_test",
#    srcs = ["test/javatests/com/example/bazel/AppTest.java"],
#    instruments = ":app",
#    manifest = "test/AndroidManifest.xml",
#    target_devices = [
#        "//tools/android/emulated_devices/generic_phone:android_21_x86",
#    ],
#    test_class = "com.example.bazel.AppTest",
#    deps = [
#        ":app_srcs",
#        "//tools/vendor/google3/aswb/third_party/android/androidx_test/espresso/core",
#        "//tools/vendor/google3/aswb/third_party/android/androidx_test/ext/junit",
#        "//tools/vendor/google3/aswb/third_party/java/android/android_sdk_linux/extras/android/compatibility/v4",
#        "//tools/vendor/google3/aswb/third_party/java/androidx/appcompat",
#        "//tools/vendor/google3/aswb/third_party/java/junit:junit-android",
#    ],
#)
