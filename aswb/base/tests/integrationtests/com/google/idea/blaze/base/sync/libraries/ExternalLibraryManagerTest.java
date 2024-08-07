/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.libraries.ExternalLibraryManager.SyncPlugin;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ExternalLibraryManager}. */
@RunWith(JUnit4.class)
public final class ExternalLibraryManagerTest extends BlazeIntegrationTestCase {
  private final MockExternalLibraryProvider libraryProvider = new MockExternalLibraryProvider();
  private SyncListener syncListener;
  private SyncPlugin syncPlugin;

  @Before
  public final void before() {
    syncPlugin = SyncPlugin.EP_NAME.findExtension(ExternalLibraryManager.SyncPlugin.class);
    syncListener =
        SyncListener.EP_NAME.findExtension(ExternalLibraryManager.StartSyncListener.class);
    registerExtension(AdditionalLibraryRootsProvider.EP_NAME, libraryProvider);
  }

  @After
  public final void after() {
    cleanUpLibraryFiles();
  }

  @Test
  public void testFilesFound() throws Exception {
    VirtualFile fooFile = workspace.createFile(new WorkspacePath("foo/Foo.java"));
    assertThat(fooFile).isNotNull();
    VirtualFile barFile = workspace.createFile(new WorkspacePath("bar/Bar.java"));
    assertThat(barFile).isNotNull();

    libraryProvider.setFiles(fooFile.getPath(), barFile.getPath());
    mockSync(SyncResult.SUCCESS);

    Collection<VirtualFile> libraryRoots = getExternalLibrary().getSourceRoots();
    assertThat(libraryRoots).containsExactly(fooFile, barFile);
  }

  @Test
  public void testFileRemoved() throws Exception {
    VirtualFile fooFile = workspace.createFile(new WorkspacePath("foo/Foo.java"));
    assertThat(fooFile).isNotNull();
    VirtualFile barFile = workspace.createFile(new WorkspacePath("bar/Bar.java"));
    assertThat(barFile).isNotNull();

    libraryProvider.setFiles(fooFile.getPath(), barFile.getPath());
    mockSync(SyncResult.SUCCESS);

    Collection<VirtualFile> libraryRoots = getExternalLibrary().getSourceRoots();
    assertThat(libraryRoots).containsExactly(fooFile, barFile);

    deleteAndWaitForVfsEvents(barFile);
    assertThat(libraryRoots).containsExactly(fooFile);
    deleteAndWaitForVfsEvents(fooFile);
    assertThat(libraryRoots).isEmpty();
  }

  @Test
  public void testSuccessfulSync() throws Exception {
    // both old and new files exist, project data is changed
    VirtualFile oldFile = workspace.createFile(new WorkspacePath("old/Old.java"));
    assertThat(oldFile).isNotNull();
    VirtualFile newFile = workspace.createFile(new WorkspacePath("new/New.java"));
    assertThat(newFile).isNotNull();

    libraryProvider.setFiles(oldFile.getPath());
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);

    libraryProvider.setFiles(newFile.getPath());
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(newFile);
  }

  @Test
  public void testFailedSync() throws Exception {
    // both old and new files exist, project data is changed
    VirtualFile oldFile = workspace.createFile(new WorkspacePath("old/Old.java"));
    assertThat(oldFile).isNotNull();
    VirtualFile newFile = workspace.createFile(new WorkspacePath("new/New.java"));
    assertThat(newFile).isNotNull();

    libraryProvider.setFiles(oldFile.getPath());
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);

    libraryProvider.setFiles(newFile.getPath());
    mockSync(SyncResult.FAILURE);
    // files list should remain the same if sync failed
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);
  }

  @Test
  public void testDuringSuccessfulSync() throws Exception {
    VirtualFile oldFile = workspace.createFile(new WorkspacePath("foo/Foo.java"));
    assertThat(oldFile).isNotNull();

    libraryProvider.setFiles(oldFile.getPath());
    mockSync(SyncResult.SUCCESS);
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isNotEmpty();

    BlazeContext context = BlazeContext.create();
    syncListener.onSyncStart(getProject(), context, SyncMode.INCREMENTAL);
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isEmpty();

    syncPlugin.refreshExecutionRoot(getProject(), MockBlazeProjectDataBuilder.builder().build());
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isNotEmpty();

    syncListener.afterSync(
        getProject(), context, SyncMode.INCREMENTAL, SyncResult.SUCCESS, ImmutableSet.of());
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isNotEmpty();
  }

  @Test
  public void testDuringFailedSync() throws Exception {
    VirtualFile oldFile = workspace.createFile(new WorkspacePath("foo/Foo.java"));
    assertThat(oldFile).isNotNull();

    libraryProvider.setFiles(oldFile.getPath());
    mockSync(SyncResult.SUCCESS);
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isNotEmpty();

    BlazeContext context = BlazeContext.create();
    syncListener.onSyncStart(getProject(), context, SyncMode.INCREMENTAL);
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isEmpty();

    syncListener.afterSync(
        getProject(), context, SyncMode.INCREMENTAL, SyncResult.FAILURE, ImmutableSet.of());
    assertThat(libraryProvider.getAdditionalProjectLibraries(getProject())).isNotEmpty();
  }

  private void mockSync(SyncResult syncResult) throws Exception {
    BlazeContext context = BlazeContext.create();
    syncListener.onSyncStart(getProject(), context, SyncMode.INCREMENTAL);
    if (syncResult.successful()) {
      syncPlugin.refreshExecutionRoot(getProject(), MockBlazeProjectDataBuilder.builder().build());
    }
    syncListener.afterSync(
        getProject(), context, SyncMode.INCREMENTAL, syncResult, ImmutableSet.of());
  }

  private SyntheticLibrary getExternalLibrary() {
    Collection<SyntheticLibrary> libraries =
        libraryProvider.getAdditionalProjectLibraries(getProject());
    assertThat(libraries).hasSize(1);
    SyntheticLibrary library = Iterables.getFirst(libraries, null);
    assertThat(library).isNotNull();
    return library;
  }

  private void deleteAndWaitForVfsEvents(VirtualFile file) {
    delete(file);
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed();
  }

  private void delete(VirtualFile file) {
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              try {
                WriteAction.run(() -> file.delete(this));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private void cleanUpLibraryFiles() {
    for (File file : libraryProvider.files) {
      VirtualFile vf = fileSystem.findFile(file.getPath());
      if (vf != null) {
        delete(vf);
      }
    }
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed();
  }

  private static final class MockExternalLibraryProvider extends BlazeExternalLibraryProvider {
    private ImmutableList<File> libraryFiles = ImmutableList.of();
    private Set<File> files = new HashSet<>();

    @Override
    protected String getLibraryName() {
      return "Mock Libraries";
    }

    @Override
    protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
      return libraryFiles;
    }

    void setFiles(String... paths) {
      this.libraryFiles = Arrays.stream(paths).map(File::new).collect(toImmutableList());
      files.addAll(libraryFiles);
    }
  }
}
