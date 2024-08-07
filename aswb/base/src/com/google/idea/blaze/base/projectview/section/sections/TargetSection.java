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

import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.BuildSystemName;
import javax.annotation.Nullable;

/** "targets" section. */
public class TargetSection {
  public static final SectionKey<TargetExpression, ListSection<TargetExpression>> KEY =
      SectionKey.of("targets");
  public static final SectionParser PARSER = new TargetSectionParser();

  private static class TargetSectionParser extends ListSectionParser<TargetExpression> {
    public TargetSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected TargetExpression parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      try {
        return TargetExpression.fromString(text);
      } catch (InvalidTargetException e) {
        parseContext.addError(e.getMessage());
        return null;
      }
    }

    @Override
    protected void printItem(TargetExpression item, StringBuilder sb) {
      sb.append(item.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Label;
    }

    @Nullable
    @Override
    public String quickDocs() {
      return "A list of build targets that will be included during sync. To resolve source files "
          + "under a project directory, the source must be reachable from one of your targets.";
    }
  }

  static class TargetsProjectViewDefaultValueProvider implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystemName buildSystemName,
        ProjectViewSet projectViewSet,
        ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      ListSection.Builder<TargetExpression> builder = ListSection.builder(KEY);
      builder.add(
          TextBlock.of(
              "  # If source code isn't resolving, add additional targets that compile it here"));
      builder.add(TextBlock.newLine());
      return ProjectView.builder(topLevelProjectView).add(builder).build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }
  }
}
