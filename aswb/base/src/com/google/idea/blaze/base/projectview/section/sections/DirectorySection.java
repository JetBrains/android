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

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
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
import com.intellij.util.PathUtil;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** "directories" section. */
public class DirectorySection {
  public static final SectionKey<DirectoryEntry, ListSection<DirectoryEntry>> KEY =
      SectionKey.of("directories");
  public static final SectionParser PARSER = new DirectorySectionParser();

  private static class DirectorySectionParser extends ListSectionParser<DirectoryEntry> {
    public DirectorySectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected DirectoryEntry parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      boolean excluded = text.startsWith("-");
      text = excluded ? text.substring(1) : text;

      // removes '.' path sections, traverses ".." without handling symlinks
      text = PathUtil.getCanonicalPath(text);

      String error = WorkspacePath.validate(text);
      if (error != null) {
        parseContext.addError(error);
        return null;
      }
      WorkspacePath directory = new WorkspacePath(text);
      return excluded ? DirectoryEntry.exclude(directory) : DirectoryEntry.include(directory);
    }

    @Override
    protected void printItem(@NotNull DirectoryEntry item, @NotNull StringBuilder sb) {
      sb.append(item.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.DirectoryItem;
    }

    @Override
    public String quickDocs() {
      return "A list of project directories that will be added as source.";
    }
  }

  static class DirectoriesProjectViewDefaultValueProvider
      implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystemName buildSystemName,
        ProjectViewSet projectViewSet,
        ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      ListSection.Builder<DirectoryEntry> builder = ListSection.builder(KEY);
      builder.add(TextBlock.of("  # Add the directories you want added as source here"));
      if (buildSystemName == BuildSystemName.Bazel) {
        builder.add(TextBlock.of("  # By default, we've added your entire workspace ('.')"));
        builder.add(DirectoryEntry.include(new WorkspacePath(".")));
      }
      builder.add(TextBlock.newLine());
      return ProjectView.builder(topLevelProjectView).add(builder).build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }
  }
}
