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

load("//tools/adt/idea/aswb/testing:test_defs.bzl", "intellij_integration_test_suite")

intellij_integration_test_suite(
    name = "tests",
    size = "large",
    srcs = ["BazelBuildServicesTest.java"],
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/m2:all",
        "//tools/adt/idea/aswb/testing/test_deps:repository_cache",
        "@aswb_test_deps//:bazel_central_registry.zip",
        "@aswb_test_deps//:projects.zip",
    ],
    env = {"BAZEL_HTTP_RULES_URLS_AS_DEFAULT_CANONICAL_ID": "0"},
    test_package_root = "com.android.tools.idea.rendering.tokens",
    deps = [
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/aswb/aswb/src/com/android/tools/idea/rendering/tokens",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:query_sync_integration_test_rules",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:query_sync_test_projects",
        "@intellij//:intellij-sdk",
    ],
)
