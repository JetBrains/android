/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.testing.java.LightJavaCodeInsightFixtureTestCase4Concrete;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySyncAsyncFileListenerTest extends LightJavaCodeInsightFixtureTestCase4Concrete {

  // Analogous to a directory included in the projectview file
  private static final Path INCLUDED_DIRECTORY = Path.of("my/project");

  private static Path projectRootPath() {
    return Path.of(LightPlatformTestCase.getSourceRoot().getPath());
  }

  @Test
  public void buildFileModified_isReported() throws Exception {
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("BUILD").toString(),
            "#");

    TestListener fileListener =
        new TestListener(getFixture().getProject())
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY));
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    assertThat(fileListener.hasModifiedBuildFiles()).isFalse();

    VirtualFile vf =
        getFixture()
            .findFileInTempDir(
                INCLUDED_DIRECTORY.resolve("BUILD").toString());
    WriteAction.runAndWait(
        () ->
            vf.setBinaryContent(
                "load(\"//something.bzl\")".getBytes(UTF_8)));
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);

    assertThat(fileListener.hasModifiedBuildFiles()).isTrue();
  }

  /**
   * Implementation of {@link QuerySyncAsyncFileListener} for testing. Has a configurable single
   * project include directory and setting for requesting syncs.
   */
  private static class TestListener extends QuerySyncAsyncFileListener {
    private Path projectInclude = null;

    public TestListener(Project project) {
      super(project);
    }

    @CanIgnoreReturnValue
    public TestListener setProjectInclude(Path path) {
      this.projectInclude = path;
      return this;
    }

    @Override
    public boolean isPathIncludedInProject(Path absolutePath) {
      return absolutePath.startsWith(projectInclude);
    }
  }
}
