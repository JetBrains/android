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
package com.google.idea.blaze.android.projectview;

import com.google.common.primitives.Ints;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import org.jetbrains.annotations.Nullable;

/** Allows project wide min sdk. */
public class AndroidMinSdkSection {
  public static final SectionKey<Integer, ScalarSection<Integer>> KEY =
      SectionKey.of("android_min_sdk");
  public static final SectionParser PARSER = new AndroidMinSdkParser();

  private static class AndroidMinSdkParser extends ScalarSectionParser<Integer> {
    AndroidMinSdkParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected Integer parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
      return Ints.tryParse(rest);
    }

    @Override
    protected void printItem(StringBuilder sb, Integer value) {
      sb.append(value);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }
}
