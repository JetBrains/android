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

import com.google.common.collect.Ordering;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
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
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import java.util.Set;
import javax.annotation.Nullable;

/** Allows users to set the rule classes they want to be imported */
public class AdditionalLanguagesSection {
  public static final SectionKey<LanguageClass, ListSection<LanguageClass>> KEY =
      SectionKey.of("additional_languages");
  public static final SectionParser PARSER = new AdditionalLanguagesSectionParser();

  private static class AdditionalLanguagesSectionParser extends ListSectionParser<LanguageClass> {
    public AdditionalLanguagesSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected LanguageClass parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      LanguageClass language = LanguageClass.fromString(text);
      if (language == null) {
        parseContext.addError("Invalid language: " + text);
        return null;
      }
      return language;
    }

    @Override
    protected void printItem(LanguageClass item, StringBuilder sb) {
      sb.append(item.getName());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "Additional languages to support in this project.";
    }
  }

  static class AdditionalLanguagesDefaultValueProvider implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystemName buildSystemName,
        ProjectViewSet projectViewSet,
        ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      Set<LanguageClass> additionalLanguages = availableAdditionalLanguages(projectViewSet);
      if (additionalLanguages.isEmpty()) {
        return topLevelProjectView;
      }
      ListSection.Builder<LanguageClass> builder = ListSection.builder(KEY);
      builder.add(TextBlock.of("  # Uncomment any additional languages you want supported"));
      additionalLanguages
          .stream()
          .sorted(Ordering.usingToString())
          .map(lang -> "  # " + lang.getName())
          .forEach(string -> builder.add(TextBlock.of(string)));
      builder.add(TextBlock.newLine());
      return ProjectView.builder(topLevelProjectView).add(builder).build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }

    private static Set<LanguageClass> availableAdditionalLanguages(ProjectViewSet projectView) {
      WorkspaceType workspaceType =
          projectView
              .getScalarValue(WorkspaceTypeSection.KEY)
              .orElse(LanguageSupport.getDefaultWorkspaceType());
      return LanguageSupport.availableAdditionalLanguages(workspaceType);
    }
  }
}
