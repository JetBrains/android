/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.sync.setup.module.SyncLibraryRegistry.LibraryToUpdate;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static com.intellij.util.ArrayUtilRt.EMPTY_FILE_ARRAY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SyncLibraryRegistry}.
 */
public class SyncLibraryRegistryTest extends IdeaTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getProject().putUserData(SyncLibraryRegistry.KEY, null); // ensure nothing is set.
  }

  public void testGetInstancePopulatesProjectLibraries() {
    List<Library> libraries = simulateProjectHasLibraries(2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());

    Map<String, Library> projectLibrariesByName = libraryRegistry.getProjectLibrariesByName();
    assertThat(projectLibrariesByName.values()).containsAllIn(libraries);

    assertNotNull(projectLibrariesByName.get("dummy1"));
    assertNotNull(projectLibrariesByName.get("dummy2"));
  }

  public void testGetInstanceReturnsSameInstance() {
    Project project = getProject();

    SyncLibraryRegistry libraryRegistry1 = SyncLibraryRegistry.getInstance(project);
    SyncLibraryRegistry libraryRegistry2 = SyncLibraryRegistry.getInstance(project);

    assertSame(libraryRegistry1, libraryRegistry2);
  }

  public void testGetInstanceReturnsNewIfExistingOneIsDisposed() {
    Project project = getProject();

    SyncLibraryRegistry libraryRegistry1 = SyncLibraryRegistry.getInstance(project);
    Disposer.dispose(libraryRegistry1);

    SyncLibraryRegistry libraryRegistry2 = SyncLibraryRegistry.getInstance(project);

    assertNotSame(libraryRegistry1, libraryRegistry2);
  }

  public void testMarkAsUsedWithLibraryInRegistry() {
    List<Library> libraries = simulateProjectHasLibraries(2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(libraries.get(0), EMPTY_FILE_ARRAY);

    assertThat(libraryRegistry.getProjectLibrariesByName().values()).containsExactly(libraries.get(1));
  }

  public void testMarkAsUsedWithLibraryNotInRegistry() {
    List<Library> libraries = simulateProjectHasLibraries(2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());

    Library library = mock(Library.class);
    when(library.getName()).thenReturn("notExisting");

    libraryRegistry.markAsUsed(library, EMPTY_FILE_ARRAY);

    assertThat(libraryRegistry.getProjectLibrariesByName().values()).containsAllIn(libraries);
  }

  public void testMarkAsUsedWhenDisposed() {
    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    Disposer.dispose(libraryRegistry);

    Library library = mock(Library.class);
    try {
      libraryRegistry.markAsUsed(library, EMPTY_FILE_ARRAY);
      fail("Expecting error due to disposed instance");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  public void testGetLibrariesToRemove() {
    List<Library> libraries = simulateProjectHasLibraries(2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(libraries.get(0), EMPTY_FILE_ARRAY);

    assertThat(libraryRegistry.getLibrariesToRemove()).containsExactly(libraries.get(1));
  }

  public void testGetLibrariesToRemoveWhenDisposed() {
    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    Disposer.dispose(libraryRegistry);

    try {
      libraryRegistry.getLibrariesToRemove();
      fail("Expecting error due to disposed instance");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  public void testGetLibrariesToUpdateWhenUrlsDidNotChange() throws IOException {
    File fakeJarPath = createTempFile("fake.jar", null);
    Library library = simulateProjectHasLibrary(fakeJarPath);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(library, fakeJarPath);

    assertThat(libraryRegistry.getLibrariesToUpdate()).isEmpty();
  }

  public void testGetLibrariesToUpdateWhenUrlsChanged() throws IOException {
    File fakeJarPath = createTempFile("fake.jar", null);
    Library library = simulateProjectHasLibrary(fakeJarPath);

    File newJarPath = createTempFile("fake2.jar", null);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(library, newJarPath);

    List<LibraryToUpdate> librariesToUpdate = libraryRegistry.getLibrariesToUpdate();
    assertThat(librariesToUpdate).hasSize(1);

    LibraryToUpdate libraryToUpdate = librariesToUpdate.get(0);
    assertSame(library, libraryToUpdate.getLibrary());
    assertThat(libraryToUpdate.getNewBinaryUrls()).containsExactly(pathToIdeaUrl(newJarPath));
  }

  public void testGetLibrariesToUpdateWhenUrlsWereAdded() throws IOException {
    File fakeJarPath = createTempFile("fake.jar", null);
    Library library = simulateProjectHasLibrary(fakeJarPath);

    File newJarPath = createTempFile("fake2.jar", null);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(library, fakeJarPath, newJarPath);

    List<LibraryToUpdate> librariesToUpdate = libraryRegistry.getLibrariesToUpdate();
    assertThat(librariesToUpdate).hasSize(1);

    LibraryToUpdate libraryToUpdate = librariesToUpdate.get(0);
    assertSame(library, libraryToUpdate.getLibrary());
    assertThat(libraryToUpdate.getNewBinaryUrls()).containsExactly(pathToIdeaUrl(fakeJarPath), pathToIdeaUrl(newJarPath));
  }

  public void testGetLibrariesToUpdateWhenUrlsWereRemoved() throws IOException {
    File jarPath1 = createTempFile("fake1.jar", null);
    File jarPath2 = createTempFile("fake2.jar", null);
    Library library = simulateProjectHasLibrary(jarPath1, jarPath2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    libraryRegistry.markAsUsed(library, jarPath1);

    List<LibraryToUpdate> librariesToUpdate = libraryRegistry.getLibrariesToUpdate();
    assertThat(librariesToUpdate).hasSize(1);

    LibraryToUpdate libraryToUpdate = librariesToUpdate.get(0);
    assertSame(library, libraryToUpdate.getLibrary());
    assertThat(libraryToUpdate.getNewBinaryUrls()).containsExactly(pathToIdeaUrl(jarPath1));
  }

  public void testGetLibrariesToUpdate() {
    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    Disposer.dispose(libraryRegistry);

    try {
      libraryRegistry.getLibrariesToUpdate();
      fail("Expecting error due to disposed instance");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  public void testDispose() {
    simulateProjectHasLibraries(2);

    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(getProject());
    Disposer.dispose(libraryRegistry);

    assertTrue(libraryRegistry.isDisposed());

    assertThat(libraryRegistry.getProjectLibrariesByName()).isEmpty();
  }

  @NotNull
  private Library simulateProjectHasLibrary(@NotNull File... binaryPaths) {
    Library library = mock(Library.class);
    when(library.getName()).thenReturn("dummy");

    int binaryPathCount = binaryPaths.length;
    String[] binaryUrls = new String[binaryPathCount];
    for (int i = 0; i < binaryPathCount; i++) {
      binaryUrls[i] = pathToIdeaUrl(binaryPaths[i]);
    }
    when(library.getUrls(OrderRootType.CLASSES)).thenReturn(binaryUrls);

    simulateProjectLibraryTableHas(new Library[]{library});
    return library;
  }

  @NotNull
  private List<Library> simulateProjectHasLibraries(int libraryCount) {
    assertThat(libraryCount).isGreaterThan(0);

    List<Library> libraries = new ArrayList<>();
    for (int i = 1; i <= libraryCount; i++) {
      Library library = mock(Library.class);
      when(library.getName()).thenReturn("dummy" + i);
      when(library.getUrls(OrderRootType.CLASSES)).thenReturn(EMPTY_STRING_ARRAY);

      libraries.add(library);
    }

    simulateProjectLibraryTableHas(libraries.toArray(new Library[libraries.size()]));
    return libraries;
  }

  private void simulateProjectLibraryTableHas(@NotNull Library[] libraries1) {
    Project project = getProject();
    ProjectLibraryTable projectLibraryTable = mock(ProjectLibraryTable.class);
    IdeComponents.replaceService(project, ProjectLibraryTable.class, projectLibraryTable);
    when(projectLibraryTable.getLibraries()).thenReturn(libraries1);
  }
}