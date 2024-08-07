
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
package com.google.idea.blaze.base.projectview.section.sections;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.intellij.openapi.diagnostic.Logger;
import javax.annotation.Nullable;
/**
 * A section to specify use query sync for new project or not. It will only take effect when
 * importing a project.
 */
public final class UseQuerySyncSection {
  private static final Logger logger = Logger.getInstance(UseQuerySyncSection.class);
  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
      SectionKey.of("use_query_sync");
  public static final SectionParser PARSER = new UseQuerySyncSectionParser();
  private static class UseQuerySyncSectionParser extends ScalarSectionParser<Boolean> {
    UseQuerySyncSectionParser() {
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
      // Do not throw exception as it's not required when users have query sync enabled via settings
      logger.info("use_query_sync is not set.");
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
      return "Enables query sync for your project.";
    }
  }
  private UseQuerySyncSection() {}
}

