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
package com.google.idea.blaze.base.lang.projectview.language;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.SectionParser.ItemType;
import com.google.idea.blaze.base.projectview.section.sections.Sections;

/** Section parser keywords accepted in project view files. */
public class ProjectViewKeywords {

  public static final ImmutableMap<String, ListSectionParser<?>> LIST_KEYWORD_MAP =
      getListKeywordMap();
  public static final ImmutableMap<String, ScalarSectionParser<?>> SCALAR_KEYWORD_MAP =
      getScalarKeywordMap();
  public static final ImmutableMap<String, ItemType> ITEM_TYPES = getItemTypes();

  private static ImmutableMap<String, ListSectionParser<?>> getListKeywordMap() {
    ImmutableMap.Builder<String, ListSectionParser<?>> builder = ImmutableMap.builder();
    for (SectionParser parser : Sections.getParsers()) {
      if (parser instanceof ListSectionParser) {
        builder.put(parser.getName(), (ListSectionParser<?>) parser);
      }
    }
    return builder.build();
  }

  /** We get the parser so we have access to both the keyword and the divider char. */
  private static ImmutableMap<String, ScalarSectionParser<?>> getScalarKeywordMap() {
    ImmutableMap.Builder<String, ScalarSectionParser<?>> builder = ImmutableMap.builder();
    for (SectionParser parser : Sections.getParsers()) {
      if (parser instanceof ScalarSectionParser) {
        builder.put(parser.getName(), (ScalarSectionParser<?>) parser);
      }
    }
    return builder.build();
  }

  private static ImmutableMap<String, ItemType> getItemTypes() {
    ImmutableMap.Builder<String, ItemType> builder = ImmutableMap.builder();
    for (SectionParser parser : Sections.getParsers()) {
      builder.put(parser.getName(), parser.getItemType());
    }
    return builder.build();
  }
}
