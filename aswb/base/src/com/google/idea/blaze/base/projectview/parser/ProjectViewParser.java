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
package com.google.idea.blaze.base.projectview.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Parses and writes project views. */
public class ProjectViewParser {

  private final BlazeContext context;
  private final WorkspacePathResolver workspacePathResolver;
  private final boolean recursive;

  Set<File> encounteredProjectViewFiles = Sets.newHashSet();
  ImmutableList.Builder<ProjectViewSet.ProjectViewFile> projectViewFiles = ImmutableList.builder();

  public ProjectViewParser(BlazeContext context, WorkspacePathResolver workspacePathResolver) {
    this.context = context;
    this.workspacePathResolver = workspacePathResolver;
    this.recursive = true;
  }

  public void parseProjectView(File projectViewFile) {
    if (!encounteredProjectViewFiles.add(projectViewFile)) {
      return;
    }
    String projectViewText = null;
    try {
      projectViewText = ProjectViewStorageManager.getInstance().loadProjectView(projectViewFile);
    } catch (IOException e) {
      // Error handled below
    }
    if (projectViewText == null) {
      IssueOutput.error(
              String.format("Could not load project view file: '%s'", projectViewFile.getPath()))
          .submit(context);
      return;
    }
    parseProjectView(
        new ParseContext(context, workspacePathResolver, projectViewFile, projectViewText));
  }

  public void parseProjectView(String text) {
    if (text.isEmpty()) {
      ProjectView projectView = new ProjectView(ImmutableList.of());
      projectViewFiles.add(new ProjectViewSet.ProjectViewFile(projectView, null));
      return;
    }
    parseProjectView(new ParseContext(context, workspacePathResolver, null, text));
  }

  private void parseProjectView(ParseContext parseContext) {
    ImmutableList.Builder<Section<?>> sections = ImmutableList.builder();

    List<SectionParser> sectionParsers = Sections.getParsers();
    while (!parseContext.atEnd()) {
      Section<?> section = null;
      for (SectionParser sectionParser : sectionParsers) {
        section = sectionParser.parse(this, parseContext);
        if (section != null) {
          sections.add(section);
          break;
        }
      }
      if (section == null) {
        if (parseContext.current().indent != 0) {
          parseContext.addError(
              String.format("Invalid indentation on line: '%s'", parseContext.current().text));
          skipSection(parseContext);
        } else {
          parseContext.addError(
              String.format("Could not parse: '%s'", parseContext.current().text));
          parseContext.consume();

          // Skip past the entire section
          skipSection(parseContext);
        }
      }
    }

    ProjectView projectView = new ProjectView(sections.build());
    projectViewFiles.add(
        new ProjectViewSet.ProjectViewFile(projectView, parseContext.getProjectViewFile()));
  }

  /** Skips all lines until the next unindented, non-empty line. */
  private static void skipSection(ParseContext parseContext) {
    while (!parseContext.atEnd() && parseContext.current().indent != 0) {
      parseContext.consume();
    }
  }

  public boolean isRecursive() {
    return recursive;
  }

  public ProjectViewSet getResult() {
    return new ProjectViewSet(projectViewFiles.build());
  }

  public static String projectViewToString(ProjectView projectView) {
    StringBuilder sb = new StringBuilder();

    List<SectionParser> sectionParsers = Sections.getParsers();
    for (Section<?> section : projectView.getSections()) {
      SectionParser sectionParser =
          sectionParsers
              .stream()
              .filter(parser -> section.isSectionType(parser.getSectionKey()))
              .findFirst()
              .orElse(null);
      if (sectionParser != null) {
        sectionParser.print(sb, section);
      }
    }

    // Because we split lines we'll always have an extra newline at the end
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    return sb.toString();
  }
}
