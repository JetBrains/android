/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import java.util.List;
import java.util.stream.Collectors;

/** List of available sections. */
public class Sections {
  // list ordering is used when constructing default template (ProjectViewDefaultValueProvider)
  private static final List<SectionParser> PARSERS =
      Lists.newArrayList(
          TextBlockSection.PARSER,
          ImportSection.PARSER,
          DirectorySection.PARSER,
          AutomaticallyDeriveTargetsSection.PARSER,
          TargetSection.PARSER,
          WorkspaceTypeSection.PARSER,
          AdditionalLanguagesSection.PARSER,
          TestSourceSection.PARSER,
          BuildFlagsSection.PARSER,
          SyncFlagsSection.PARSER,
          TestFlagsSection.PARSER,
          ImportTargetOutputSection.PARSER,
          ExcludeTargetSection.PARSER,
          ExcludedSourceSection.PARSER,
          RunConfigurationsSection.PARSER,
          ShardBlazeBuildsSection.PARSER,
          TargetShardSizeSection.PARSER,
          BazelBinarySection.PARSER,
          BuildConfigSection.PARSER,
          UseQuerySyncSection.PARSER);

  public static List<SectionParser> getParsers() {
    List<SectionParser> parsers = Lists.newArrayList(PARSERS);
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      parsers.addAll(syncPlugin.getSections());
    }
    return parsers;
  }

  public static List<SectionParser> getUndeprecatedParsers() {
    return getParsers().stream().filter(p -> !p.isDeprecated()).collect(Collectors.toList());
  }
}
