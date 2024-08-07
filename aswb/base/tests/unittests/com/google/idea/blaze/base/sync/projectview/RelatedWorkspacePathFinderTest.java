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
package com.google.idea.blaze.base.sync.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.testing.IntellijRule;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RelatedWorkspacePathFinder} */
@RunWith(JUnit4.class)
public class RelatedWorkspacePathFinderTest {

  private static final File WORKSPACE_ROOT = new File("/workspace");

  @Rule public IntellijRule intellij = new IntellijRule();

  private MockFileOperationProvider files;
  private RelatedWorkspacePathFinder relatedPathFinder;
  private WorkspacePathResolver workspacePathResolver;

  @Before
  public void setUp() throws IOException {
    files = new MockFileOperationProvider();
    intellij.registerApplicationService(FileOperationProvider.class, files);
    relatedPathFinder = new RelatedWorkspacePathFinder();
    workspacePathResolver = new WorkspacePathResolverImpl(new WorkspaceRoot(WORKSPACE_ROOT));
  }

  @Test
  public void initialJavaDirectory() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/com/google"));
    files.mkdirs(new File(WORKSPACE_ROOT, "javatests/com/google"));

    WorkspacePath initialPath = new WorkspacePath("java/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).hasSize(1);
    assertThat(relatedPaths).containsExactly(new WorkspacePath("javatests/com/google"));
  }

  @Test
  public void middleJavaDirectory() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/java/com/google"));
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/javatests/com/google"));

    WorkspacePath initialPath = new WorkspacePath("srcs/java/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).hasSize(1);
    assertThat(relatedPaths).containsExactly(new WorkspacePath("srcs/javatests/com/google"));
  }

  @Test
  public void finalJavaDirectory() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/java"));
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/javatests"));

    WorkspacePath initialPath = new WorkspacePath("srcs/java");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).hasSize(1);
    assertThat(relatedPaths).containsExactly(new WorkspacePath("srcs/javatests"));
  }

  @Test
  public void noJavaDirectory() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/com/google"));

    WorkspacePath initialPath = new WorkspacePath("srcs/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).isEmpty();
  }

  @Test
  public void javatestsDirectoryDoesNotExist() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/com/google"));

    WorkspacePath initialPath = new WorkspacePath("java/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).isEmpty();
  }

  @Test
  public void javaInMiddleOfWord() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/cooljavastuff/com/google"));
    files.mkdirs(new File(WORKSPACE_ROOT, "srcs/cooljavatestsstuff/com/google"));

    WorkspacePath initialPath = new WorkspacePath("java/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).isEmpty();
  }

  @Test
  public void javatestsExistsButSubdirectoryDoesNotExist() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/com/google"));
    files.mkdirs(new File(WORKSPACE_ROOT, "javatests/com"));

    WorkspacePath initialPath = new WorkspacePath("java/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).isEmpty();
  }

  @Test
  public void onlyReplacesFirstFoundJavaPath() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/src/java"));
    files.mkdirs(new File(WORKSPACE_ROOT, "javatests/src/java"));
    files.mkdirs(new File(WORKSPACE_ROOT, "javatests/src/javatests"));

    WorkspacePath initialPath = new WorkspacePath("java/src/java");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).hasSize(1);
    assertThat(relatedPaths).containsExactly(new WorkspacePath("javatests/src/java"));
  }

  @Test
  public void skipsFirstJavaIfMatchingJavatestsIsNotFound() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/src/java"));
    files.mkdirs(new File(WORKSPACE_ROOT, "java/src/javatests"));

    WorkspacePath initialPath = new WorkspacePath("java/src/java");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).hasSize(1);
    assertThat(relatedPaths).containsExactly(new WorkspacePath("java/src/javatests"));
  }

  @Test
  public void javatestsIsNotRelatedToJava() throws Exception {
    files.mkdirs(new File(WORKSPACE_ROOT, "java/com/google"));
    files.mkdirs(new File(WORKSPACE_ROOT, "javatests/com/google"));

    WorkspacePath initialPath = new WorkspacePath("javatests/com/google");
    ImmutableSet<WorkspacePath> relatedPaths =
        relatedPathFinder.findRelatedWorkspaceDirectories(workspacePathResolver, initialPath);

    assertThat(relatedPaths).isEmpty();
  }

  private static class MockFileOperationProvider extends FileOperationProvider {

    private final Set<File> existingFiles = new HashSet<>();

    @Override
    public boolean exists(File file) {
      return existingFiles.contains(file);
    }

    @Override
    public boolean mkdirs(File file) {
      while (file != null && !existingFiles.contains(file)) {
        existingFiles.add(file);
        file = file.getParentFile();
      }
      return true;
    }
  }
}
