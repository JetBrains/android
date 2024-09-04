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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ProjectViewVerifier */
@RunWith(JUnit4.class)
public class ProjectViewVerifierTest extends BlazeTestCase {

  private static final String FAKE_ROOT = "/root";
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_ROOT));
  private WorkspacePathResolver workspacePathResolver =
      new WorkspacePathResolverImpl(workspaceRoot);
  private MockFileOperationProvider fileOperationProvider;
  private ErrorCollector errorCollector = new ErrorCollector();
  private BlazeContext context;
  private WorkspaceLanguageSettings workspaceLanguageSettings =
      new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA));

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<BlazeSyncPlugin> ep =
        registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
    ep.registerExtension(
        new BlazeSyncPlugin() {
          @Override
          public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
            return ImmutableList.of(WorkspaceType.JAVA);
          }

          @Override
          public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
            return ImmutableSet.of(LanguageClass.JAVA);
          }
        });

    fileOperationProvider = new MockFileOperationProvider(workspaceRoot);
    applicationServices.register(FileOperationProvider.class, fileOperationProvider);
    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  public void testNoIssues() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example")))
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example2"))))
                    .build())
            .build();
    fileOperationProvider.addProjectView(projectViewSet);
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertNoIssues();
  }

  @Test
  public void testExcludingExactRootResultsInIssue() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example")))
                            .add(
                                DirectoryEntry.exclude(
                                    new WorkspacePath("java/com/google/android/apps/example"))))
                    .build())
            .build();
    fileOperationProvider.addProjectView(projectViewSet);
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
        "java/com/google/android/apps/example is included, "
            + "but that contradicts java/com/google/android/apps/example which was excluded");
  }

  @Test
  public void testExcludingRootViaParentResultsInIssue() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example")))
                            .add(
                                DirectoryEntry.exclude(
                                    new WorkspacePath("java/com/google/android/apps"))))
                    .build())
            .build();
    fileOperationProvider.addProjectView(projectViewSet);
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
        "java/com/google/android/apps/example is included, "
            + "but that contradicts java/com/google/android/apps which was excluded");
  }

  @Test
  public void testExcludingSubdirectoryOfRootResultsInNoIssues() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example")))
                            .add(
                                DirectoryEntry.exclude(
                                    new WorkspacePath(
                                        "java/com/google/android/apps/example/subdir"))))
                    .build())
            .build();
    fileOperationProvider.addProjectView(projectViewSet);
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertNoIssues();
  }

  @Test
  public void testImportRootMissingResultsInIssue() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example"))))
                    .build())
            .build();
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
        String.format(
            "Directory '%s' specified in project view not found.",
            "java/com/google/android/apps/example"));
  }

  @Test
  public void testImportRootIsFileResultsInIssue() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(DirectorySection.KEY)
                            .add(
                                DirectoryEntry.include(
                                    new WorkspacePath("java/com/google/android/apps/example"))))
                    .build())
            .build();
    fileOperationProvider.addFile(new WorkspacePath("java/com/google/android/apps/example"));
    ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    errorCollector.assertIssues(
        String.format(
            "Directory '%s' specified in project view is a file.",
            "java/com/google/android/apps/example"));
  }

  static class MockFileOperationProvider extends FileOperationProvider {

    private final WorkspaceRoot workspaceRoot;
    private final Set<File> files = Sets.newHashSet();
    private final Set<File> directories = Sets.newHashSet();

    MockFileOperationProvider(WorkspaceRoot workspaceRoot) {
      this.workspaceRoot = workspaceRoot;
    }

    @CanIgnoreReturnValue
    MockFileOperationProvider addFile(WorkspacePath file) {
      files.add(workspaceRoot.fileForPath(file));
      return this;
    }

    @CanIgnoreReturnValue
    MockFileOperationProvider addDirectory(WorkspacePath file) {
      addFile(file);
      directories.add(workspaceRoot.fileForPath(file));
      return this;
    }

    @CanIgnoreReturnValue
    MockFileOperationProvider addPackage(WorkspacePath file) {
      addFile(new WorkspacePath(file + "/BUILD"));
      addDirectory(file);
      return this;
    }

    @CanIgnoreReturnValue
    MockFileOperationProvider addPackages(Iterable<WorkspacePath> files) {
      for (WorkspacePath workspacePath : files) {
        addPackage(workspacePath);
      }
      return this;
    }

    @CanIgnoreReturnValue
    MockFileOperationProvider addImportRoots(ImportRoots importRoots) {
      addPackages(importRoots.rootDirectories());
      addPackages(importRoots.excludeDirectories());
      return this;
    }

    MockFileOperationProvider addProjectView(ProjectViewSet projectViewSet) {
      ImportRoots importRoots =
          ImportRoots.builder(workspaceRoot, BuildSystemName.Blaze).add(projectViewSet).build();
      return addImportRoots(importRoots);
    }

    @Override
    public boolean exists(File file) {
      return files.contains(file);
    }

    @Override
    public boolean isDirectory(File file) {
      return directories.contains(file);
    }
  }
}
