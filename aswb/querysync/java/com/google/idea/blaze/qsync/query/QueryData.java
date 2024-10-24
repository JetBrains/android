/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;

/** Contains records for storing query summary data, as an alternative to protos. */
public class QueryData {

  /**
   * Native representation of a the parts we use from {@link
   * com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule}.
   */
  public record Rule(
      String ruleClass,
      ImmutableList<String> sources,
      ImmutableList<String> deps,
      ImmutableList<String> idlSources,
      ImmutableList<String> runtimeDeps,
      ImmutableList<String> resourceFiles,
      String manifest,
      String testApp,
      String instruments,
      String customPackage,
      ImmutableList<String> hdrs,
      ImmutableList<String> copts,
      ImmutableList<String> tags,
      String mainClass) {

    public static final Rule EMPTY =
        new AutoBuilder_QueryData_Rule_Builder()
            .ruleClass("")
            .sources(ImmutableList.of())
            .deps(ImmutableList.of())
            .idlSources(ImmutableList.of())
            .runtimeDeps(ImmutableList.of())
            .resourceFiles(ImmutableList.of())
            .manifest("")
            .testApp("")
            .instruments("")
            .customPackage("")
            .hdrs(ImmutableList.of())
            .copts(ImmutableList.of())
            .tags(ImmutableList.of())
            .mainClass("")
            .build();

    public Builder toBuilder() {
      return new AutoBuilder_QueryData_Rule_Builder(this);
    }

    public static Builder builder() {
      return EMPTY.toBuilder();
    }

    @AutoBuilder
    public interface Builder {

      Builder ruleClass(String value);

      Builder sources(ImmutableList<String> value);

      Builder deps(ImmutableList<String> value);

      Builder idlSources(ImmutableList<String> sources);

      Builder runtimeDeps(ImmutableList<String> sources);

      Builder resourceFiles(ImmutableList<String> sources);

      Builder manifest(String value);

      Builder testApp(String value);

      Builder instruments(String value);

      Builder customPackage(String value);

      Builder hdrs(ImmutableList<String> sources);

      Builder copts(ImmutableList<String> sources);

      Builder tags(ImmutableList<String> sources);

      Builder mainClass(String value);

      Rule build();
    }
  }
}
