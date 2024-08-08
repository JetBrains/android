/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import javax.annotation.Nullable;

/** Allows the user to tune the maximum number of targets in each blaze build shard. */
public class TargetShardSizeSection {
  public static final SectionKey<Integer, ScalarSection<Integer>> KEY =
      SectionKey.of("target_shard_size");
  public static final SectionParser PARSER = new TargetShardSizeSectionParser();

  private static class TargetShardSizeSectionParser extends ScalarSectionParser<Integer> {
    TargetShardSizeSectionParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected Integer parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
      try {
        return Integer.parseInt(rest);
      } catch (NumberFormatException e) {
        parseContext.addError(
            String.format("Invalid shard size '%s': Shard size must be an integer", rest));
        return null;
      }
    }

    @Override
    protected void printItem(StringBuilder sb, Integer value) {
      sb.append(value.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "Sets the maximum number of targets per shard, when sharding build invocations during "
          + "sync. Only relevant if 'shard_sync: true' is also set";
    }
  }
}
