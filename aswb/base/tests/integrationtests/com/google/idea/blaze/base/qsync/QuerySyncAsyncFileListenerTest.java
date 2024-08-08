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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.qsync.QuerySyncAsyncFileListener.SyncRequester;
import com.google.idea.testing.java.CompatLightJavaCodeInsightFixtureTestCase4;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class QuerySyncAsyncFileListenerTest extends CompatLightJavaCodeInsightFixtureTestCase4 {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SyncRequester mockSyncRequester;

  // Analogous to a directory included in the projectview file
  private static final Path INCLUDED_DIRECTORY = Path.of("my/project");

  private static Path projectRootPath() {
    return Path.of(LightPlatformTestCase.getSourceRoot().getPath());
  }

  @Test
  public void projectFileAdded_requestsSync() {
    QuerySyncAsyncFileListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(true);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    verify(mockSyncRequester, never()).requestSync();

    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
            "package com.example;public class Class1{}");
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
    verify(mockSyncRequester, atLeastOnce()).requestSync();
  }

  @Test
  public void projectFileAdded_autoSyncDisabled_neverRequestsSync() {
    QuerySyncAsyncFileListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(false);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());

    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
            "package com.example;public class Class1{}");
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
    verify(mockSyncRequester, never()).requestSync();
  }

  @Test
  public void nonProjectFileAdded_neverRequestsSync() {
    QuerySyncAsyncFileListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(true);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    verify(mockSyncRequester, never()).requestSync();

    getFixture()
        .addFileToProject(
            "some/other/path/Class1.java", "package some.other.path;public class Class1{}");
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
    verify(mockSyncRequester, never()).requestSync();
  }

  @Test
  public void projectFileMoved_requestsSync() throws Exception {
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
            "package com.example;public class Class1 {}");
    getFixture()
        .getTempDirFixture()
        .findOrCreateDir(INCLUDED_DIRECTORY.resolve("submodule/java/com/example").toString());

    QuerySyncAsyncFileListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(true);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    verify(mockSyncRequester, never()).requestSync();

    WriteAction.runAndWait(
        () ->
            getFixture()
                .moveFile(
                    INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
                    INCLUDED_DIRECTORY.resolve("submodule/java/com/example").toString()));
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
    verify(mockSyncRequester, atLeastOnce()).requestSync();
  }

  @Test
  public void projectFileModified_nonBuildFile_doesNotRequestSync() throws Exception {
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
            "package com.example;public class Class1 {}");

    TestListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(true);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    verify(mockSyncRequester, never()).requestSync();

    VirtualFile vf =
        getFixture()
            .findFileInTempDir(
                INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString());
    WriteAction.runAndWait(
        () ->
            vf.setBinaryContent(
                "/**LICENSE-TEXT*/package com.example;public class Class1{}".getBytes(UTF_8)));
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);

    verify(mockSyncRequester, never()).requestSync();
    assertThat(fileListener.hasModifiedBuildFiles()).isFalse();
  }

  @Test
  public void projectFileModified_buildFile_requestsSync() throws Exception {
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("BUILD").toString(), "java_library(name=\"java\",srcs=[])");

    TestListener fileListener =
        new TestListener(getFixture().getProject(), mockSyncRequester)
            .setProjectInclude(projectRootPath().resolve(INCLUDED_DIRECTORY))
            .setAutoSync(true);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
    verify(mockSyncRequester, never()).requestSync();

    VirtualFile vf = getFixture().findFileInTempDir(INCLUDED_DIRECTORY.resolve("BUILD").toString());
    WriteAction.runAndWait(
        () ->
            vf.setBinaryContent(
                "/**LICENSE-TEXT*/java_library(name=\"javalib\",srcs=[])".getBytes(UTF_8)));
    ApplicationManager.getApplication()
        .invokeAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);

    verify(mockSyncRequester, atLeastOnce()).requestSync();
    assertThat(fileListener.hasModifiedBuildFiles()).isTrue();
  }

  /**
   * Implementation of {@link QuerySyncAsyncFileListener} for testing. Has a configurable single
   * project include directory and setting for requesting syncs.
   */
  private static class TestListener extends QuerySyncAsyncFileListener {
    private Path projectInclude = null;
    private boolean autoSync;

    public TestListener(Project project, SyncRequester syncRequester) {
      super(project, syncRequester);
    }

    @CanIgnoreReturnValue
    public TestListener setProjectInclude(Path path) {
      this.projectInclude = path;
      return this;
    }

    @CanIgnoreReturnValue
    public TestListener setAutoSync(boolean value) {
      this.autoSync = value;
      return this;
    }

    @Override
    public boolean isPathIncludedInProject(Path absolutePath) {
      return absolutePath.startsWith(projectInclude);
    }

    @Override
    public boolean syncOnFileChanges() {
      return autoSync;
    }
  }
}
