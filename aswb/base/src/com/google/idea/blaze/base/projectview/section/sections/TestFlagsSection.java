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

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.Blaze;
import javax.annotation.Nullable;

/** Section for test-specific default blaze_flags. */
public class TestFlagsSection {
  public static final SectionKey<String, ListSection<String>> KEY = SectionKey.of("test_flags");
  public static final SectionParser PARSER = new TestFlagsSectionParser();

  static class TestFlagsSectionParser extends ListSectionParser<String> {
    TestFlagsSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext) {
      return parseContext.current().text;
    }

    @Override
    protected void printItem(String item, StringBuilder sb) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return String.format(
          "A set of flags that get passed to all %s test command invocations as arguments.",
          Blaze.guessBuildSystemName());
    }
  }
}
