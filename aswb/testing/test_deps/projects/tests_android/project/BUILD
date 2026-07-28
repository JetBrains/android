# Copyright 2026 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_android//rules:rules.bzl", "android_binary", "android_library")
load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")
load(":stub.bzl", "android_instrumentation_test")

package(
    default_visibility = ["//visibility:public"],
)

android_library(
    name = "lib",
    manifest = "AndroidManifest.xml",
)

android_binary(
    name = "app",
    manifest = "AndroidManifest.xml",
    deps = [":lib"],
)

kt_android_library(
    name = "test_lib",
    srcs = [
        "java/com/example/tests/JavaAndroidTest.java",
        "java/com/example/tests/KotlinAndroidTest.kt",
    ],
    deps = [
        ":lib",
        "@rules_android_maven//:junit_junit",
    ],
)

android_binary(
    name = "test_app",
    instruments = ":app",
    manifest = "TestAndroidManifest.xml",
    deps = [":test_lib"],
)

android_instrumentation_test(
    name = "instrumentation_test",
    target_device = ":dummy_device",
    test_app = ":test_app",
    deps = [":test_app"],
)

filegroup(
    name = "dummy_device",
)
