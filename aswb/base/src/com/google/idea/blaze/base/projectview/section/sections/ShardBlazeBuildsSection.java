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

/** Set for particularly large projects to enable sharding blaze invocations during blaze sync. */
public class ShardBlazeBuildsSection {
  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY = SectionKey.of("shard_sync");
  public static final SectionParser PARSER = new ShardBlazeSyncSectionParser();

  private static class ShardBlazeSyncSectionParser extends ScalarSectionParser<Boolean> {
    ShardBlazeSyncSectionParser() {
      super(KEY, ':');
    }

    @Override
    @Nullable
    protected Boolean parseItem(ProjectViewParser parser, ParseContext parseContext, String text) {
      if (text.equals("true")) {
        return true;
      }
      if (text.equals("false")) {
        return false;
      }
      parseContext.addError(
          "'shard_sync' must be set to 'true' or 'false' (e.g. 'shard_sync: true')");
      return null;
    }

    @Override
    protected void printItem(StringBuilder sb, Boolean item) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "Allows sharding build invocations when syncing and compiling your project.";
    }
  }
}
