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

"""Stub implementation of build_dependencies_kotlin_deps.bzl"""

def _get_followed_kotlin_dependencies(rule):
    return []

def _get_kotlin_info_v2(target, rule, info):
    return None

IDE_KOTLIN = struct(
    srcs_attributes = [],
    follow_attributes = [],
    follow_additional_attributes = [],
    followed_dependencies = _get_followed_kotlin_dependencies,
    toolchains_aspects = [],
    get_kotlin_info_v2 = _get_kotlin_info_v2,
)
