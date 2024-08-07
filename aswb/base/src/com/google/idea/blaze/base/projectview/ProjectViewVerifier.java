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
package com.google.idea.blaze.base.projectview;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Verifies project views. */
public class ProjectViewVerifier {

  /** Verifies the project view. Any errors are output to the context as issues. */
  public static boolean verifyProjectView(
      @Nullable Project project,
      BlazeContext context,
      WorkspacePathResolver workspacePathResolver,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!verifyIncludedPackagesAreNotExcluded(context, projectViewSet)) {
      return false;
    }
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      if (!syncPlugin.validateProjectView(
          project, context, projectViewSet, workspaceLanguageSettings)) {
        return false;
      }
    }
    if (!LanguageSupport.validateLanguageSettings(context, workspaceLanguageSettings)) {
      return false;
    }
    warnAboutDeprecatedSections(context, projectViewSet);
    if (!verifyIncludedPackagesExistOnDisk(context, workspacePathResolver, projectViewSet)) {
      return false;
    }
    return true;
  }

  private static void warnAboutDeprecatedSections(
      BlazeContext context, ProjectViewSet projectViewSet) {
    List<SectionParser> deprecatedParsers =
        Sections.getParsers().stream().filter(SectionParser::isDeprecated).collect(toList());
    for (SectionParser sectionParser : deprecatedParsers) {
      for (ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
        ProjectView projectView = projectViewFile.projectView;
        if (projectView
            .getSections()
            .stream()
            .anyMatch(section -> section.isSectionType(sectionParser.getSectionKey()))) {
          String deprecationMessage = sectionParser.getDeprecationMessage();
          if (deprecationMessage == null) {
            deprecationMessage = String.format("%s is deprecated", sectionParser.getName());
          }
          IssueOutput.warn(deprecationMessage)
              .inFile(projectViewFile.projectViewFile)
              .submit(context);
        }
      }
    }
  }

  private static boolean verifyIncludedPackagesAreNotExcluded(
      BlazeContext context, ProjectViewSet projectViewSet) {
    boolean ok = true;

    List<WorkspacePath> includedDirectories =
        projectViewSet
            .listItems(DirectorySection.KEY)
            .stream()
            .filter(entry -> entry.included)
            .map(entry -> entry.directory)
            .collect(toList());

    for (WorkspacePath includedDirectory : includedDirectories) {
      for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
        List<DirectoryEntry> directoryEntries = Lists.newArrayList();
        for (ListSection<DirectoryEntry> section :
            projectViewFile.projectView.getSectionsOfType(DirectorySection.KEY)) {
          directoryEntries.addAll(section.items());
        }

        for (DirectoryEntry entry : directoryEntries) {
          if (entry.included) {
            continue;
          }

          WorkspacePath excludedDirectory = entry.directory;
          if (FileUtil.isAncestor(
              excludedDirectory.relativePath(), includedDirectory.relativePath(), false)) {
            String message =
                String.format(
                    "%s is included, but that contradicts %s which was excluded",
                    includedDirectory, excludedDirectory);
            IssueOutput.error(message).inFile(projectViewFile.projectViewFile).submit(context);
            context.output(new PrintOutput(message, OutputType.ERROR));
            ok = false;
          }
        }
      }
    }
    return ok;
  }

  private static boolean verifyIncludedPackagesExistOnDisk(
      BlazeContext context,
      WorkspacePathResolver workspacePathResolver,
      ProjectViewSet projectViewSet) {
    boolean ok = true;

    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();

    for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
      List<DirectoryEntry> directoryEntries = Lists.newArrayList();
      for (ListSection<DirectoryEntry> section :
          projectViewFile.projectView.getSectionsOfType(DirectorySection.KEY)) {
        directoryEntries.addAll(section.items());
      }
      for (DirectoryEntry entry : directoryEntries) {
        if (!entry.included) {
          continue;
        }
        WorkspacePath workspacePath = entry.directory;
        File file = workspacePathResolver.resolveToFile(workspacePath);
        if (!fileOperationProvider.exists(file)) {
          String message =
              String.format("Directory '%s' specified in project view not found.", workspacePath);
          IssueOutput.error(message).inFile(projectViewFile.projectViewFile).submit(context);
          context.output(new PrintOutput(message, OutputType.ERROR));
          ok = false;
        } else if (!fileOperationProvider.isDirectory(file)) {
          String message =
              String.format("Directory '%s' specified in project view is a file.", workspacePath);
          IssueOutput.error(message).inFile(projectViewFile.projectViewFile).submit(context);
          context.output(new PrintOutput(message, OutputType.ERROR));
          ok = false;
        }
      }
    }
    return ok;
  }
}
