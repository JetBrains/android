# Copyright 2025 The Bazel Authors. All rights reserved.
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

load("@rules_java//java:java_library.bzl", "java_library")
load("@rules_java//java:java_test.bzl", "java_test")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "intellij_integration_test_suite")

java_test(
    name = "BazelClassFileFinderTest",
    srcs = ["BazelClassFileFinderTest.java"],
    data = [
        ":helloworld",
        "//tools/adt/idea/aswb/aswb/tests/unittests2/com/android/tools/idea/rendering/tokens:helloworld",
    ],
    test_class = "com.android.tools.idea.rendering.tokens.BazelClassFileFinderTest",
    runtime_deps = ["//tools/adt/idea/project-system:intellij.android.projectSystem"],
    deps = [
        "//tools/adt/idea/aswb/aswb/src/com/android/tools/idea/rendering/tokens",
        "//tools/adt/idea/aswb/testing/src/com/google/idea/testing/runfiles",
        "@maven//:junit.junit",
    ],
)

java_library(
    name = "helloworld",
    srcs = ["HelloWorld.java"],
)
