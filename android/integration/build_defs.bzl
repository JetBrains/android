# The version of Gradle to use for integration tests. This must be kept
# in-sync with code (search the codebase for
# "INTEGRATION_TEST_GRADLE_VERSION").
INTEGRATION_TEST_GRADLE_VERSION = "//tools/base/build-system:gradle-distrib-7.5"

# The emulator to use for integration tests. This must be kept in-sync with
# code (search the codebase for "INTEGRATION_TEST_SYSTEM_IMAGE").
INTEGRATION_TEST_SYSTEM_IMAGE = "@system_image_android-31_default_x86_64//:x86_64-android-31-images"
