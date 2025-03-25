/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.deps;

/** Represents an output group produced by the {@code build_dependencies.bzl} aspect. */
public enum OutputGroup {
  JARS("qs_jars"),
  AARS("qs_aars"),
  GENSRCS("qs_gensrcs"),
  ARTIFACT_INFO_FILE("qs_info"),
  CC_HEADERS("qs_cc_headers"),
  CC_INFO_FILE("qs_cc_info");

  private final String name;

  OutputGroup(String name) {
    this.name = name;
  }

  public String outputGroupName() {
    return name;
  }
}
