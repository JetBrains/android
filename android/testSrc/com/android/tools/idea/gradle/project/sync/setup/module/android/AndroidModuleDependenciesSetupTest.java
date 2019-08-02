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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.openapi.roots.DependencyScope.RUNTIME;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTableImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.testFramework.IdeaTestCase;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidModuleDependenciesSetup}.
 */
public class AndroidModuleDependenciesSetupTest extends IdeaTestCase {
  @Mock private LibraryFilePaths myLibraryFilePaths;

  private AndroidModuleDependenciesSetup myDependenciesSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(myProject).replaceProjectService(LibraryFilePaths.class, myLibraryFilePaths);
    myDependenciesSetup = new AndroidModuleDependenciesSetup();
  }

  public void testSetUpLibraryWithExistingLibrary() throws IOException {
    File binaryPath = createTempFile("fakeLibrary.jar", "");
    File sourcePath = createTempFile("fakeLibrary-sources.jar", "");
    File javadocPath = createTempFile("fakeLibrary-javadoc.jar", "");
    Library newLibrary = createLibrary(binaryPath, sourcePath, javadocPath);

    String libraryName = binaryPath.getName();
    Module module = getModule();

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths = {binaryPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, binaryPath, binaryPaths, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.
    LibraryOrderEntry libraryOrderEntry = libraryOrderEntries.get(0);
    assertSame(newLibrary, libraryOrderEntry.getLibrary()); // The existing library should not have been changed.

    // Should not attempt to look up sources and documentation for existing libraries.
    verify(myLibraryFilePaths, never()).findSourceJarPath(libraryName, binaryPath);
    verify(myLibraryFilePaths, never()).findJavadocJarPath(libraryName, javadocPath);
  }

  public void testSetUpLibraryTwiceWithSameLibraryInDifferentScopes() throws IOException {
    File binaryPath = createTempFile("fakeLibrary.jar", "");
    File sourcePath = createTempFile("fakeLibrary-sources.jar", "");
    File javadocPath = createTempFile("fakeLibrary-javadoc.jar", "");
    Library newLibrary = createLibrary(binaryPath, sourcePath, javadocPath);

    String libraryName = binaryPath.getName();
    Module module = getModule();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths = {binaryPath};

    // Add newLibrary twice with different scopes.
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, PROVIDED, binaryPath, binaryPaths, false);
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, RUNTIME, binaryPath, binaryPaths, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    // Verify that there're two library order entries for newLibrary.
    assertThat(libraryOrderEntries).hasSize(2);
    List<Library> libraries = libraryOrderEntries.stream().map(LibraryOrderEntry::getLibrary).collect(Collectors.toList());
    assertThat(libraries).containsExactly(newLibrary, newLibrary);

    // Verify that the scopes are PROVIDED and RUNTIME.
    List<DependencyScope> scopes = libraryOrderEntries.stream().map(LibraryOrderEntry::getScope).collect(Collectors.toList());
    assertThat(scopes).containsExactly(PROVIDED, RUNTIME);
  }

  @NotNull
  private Library createLibrary(@NotNull File binaryPath, @NotNull File sourcePath, @NotNull File javadocPath) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(getProject());
    LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
    Library library = libraryTableModel.createLibrary("Gradle: " + binaryPath.getName());

    Application application = ApplicationManager.getApplication();
    application.runWriteAction(libraryTableModel::commit);

    Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(pathToIdeaUrl(binaryPath), CLASSES);
    libraryModel.addRoot(pathToIdeaUrl(sourcePath), SOURCES);
    libraryModel.addRoot(pathToIdeaUrl(javadocPath), JavadocOrderRootType.getInstance());

    application.runWriteAction(libraryModel::commit);

    return library;
  }

  public void testSetUpLibraryWithNewLibrary() throws IOException {
    File binaryPath = createTempFile("fakeLibrary.jar", "");
    File sourcePath = createTempFile("fakeLibrary-sources.jar", "");
    File javadocPath = createTempFile("fakeLibrary-javadoc.jar", "");

    String libraryName = "Gradle: " + binaryPath.getName();
    Module module = getModule();
    when(myLibraryFilePaths.findSourceJarPath(libraryName, binaryPath)).thenReturn(sourcePath);
    when(myLibraryFilePaths.findJavadocJarPath(libraryName, binaryPath)).thenReturn(javadocPath);

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths = {binaryPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, binaryPath, binaryPaths, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.

    // Check that library entry is not exported. b/62265305.
    LibraryOrderEntry orderEntry = libraryOrderEntries.get(0);
    assertFalse(orderEntry.isExported());

    Library library = orderEntry.getLibrary();
    assertNotNull(library);
    assertEquals(libraryName, library.getName());

    String[] binaryUrls = library.getUrls(CLASSES);
    assertThat(binaryUrls).hasLength(1);
    assertEquals(pathToIdeaUrl(binaryPath), binaryUrls[0]);

    String[] sourceUrls = library.getUrls(SOURCES);
    assertThat(sourceUrls).hasLength(1);
    assertEquals(pathToIdeaUrl(sourcePath), sourceUrls[0]);

    String[] javadocUrls = library.getUrls(JavadocOrderRootType.getInstance());
    assertThat(javadocUrls).hasLength(1);
    assertEquals(pathToIdeaUrl(javadocPath), javadocUrls[0]);

    verify(myLibraryFilePaths).findSourceJarPath(libraryName, binaryPath);
    // Documentation paths are populated at the LibraryDependency level - no look-up to be done during setup itself
    verify(myLibraryFilePaths, never()).findJavadocJarPath(libraryName, javadocPath);
  }

  public void testSetupWithChangedPaths() throws IOException {
    File cachedPath = createTempFile("fakeLibrary.jar", "");
    File sourcePath = createTempFile("fakeLibrary-sources.jar", "");
    File javadocPath = createTempFile("fakeLibrary-javadoc.jar", "");
    assertTrue(cachedPath.delete());
    // Library should only have sources added by url.
    Library newLibrary = createLibrary(cachedPath, sourcePath, javadocPath);

    long libraryTableModCount = getLibraryTableModeCount(myProject);

    String libraryName = cachedPath.getName();
    Module module = getModule();

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File newBinaryPath = createTempFile("newFakeLibrary.jar", "");
    File[] newBinaryPaths = {newBinaryPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, newBinaryPath, newBinaryPaths, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.
    LibraryOrderEntry libraryOrderEntry = libraryOrderEntries.get(0);
    assertNotSame(newLibrary, libraryOrderEntry.getLibrary()); // The existing library should have been recreated.
    assertTrue(libraryTableModCount < getLibraryTableModeCount(myProject));
  }

  /**
   * @return the current modification count of the {@link ProjectLibraryTable} for the current project.
   */
  public static long getLibraryTableModeCount(@NotNull Project project) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    assertInstanceOf(libraryTable, ProjectLibraryTableImpl.class);
    ProjectLibraryTableImpl libraryTableImpl = (ProjectLibraryTableImpl)libraryTable;
    return libraryTableImpl.getStateModificationCount();
  }

  @NotNull
  private static List<LibraryOrderEntry> getLibraryOrderEntries(@NotNull Module module) {
    List<LibraryOrderEntry> libraryOrderEntries = new ArrayList<>();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();

    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        libraryOrderEntries.add((LibraryOrderEntry)orderEntry);
      }
    }
    return libraryOrderEntries;
  }

  public void testSetUpLibraryWithSameNameDifferentPath() throws IOException {
    File cachedBinaryPath = createTempFile("cachedFakeLibrary.jar", "");
    File cachedSourcePath = createTempFile("cachedFakeLibrary-sources.jar", "");
    File cachedJavadocPath = createTempFile("cachedFakeLibrary-javadoc.jar", "");
    Library cachedLibrary = createLibrary(cachedBinaryPath, cachedSourcePath, cachedJavadocPath);

    File updatedBinaryPath = createTempFile("updatedFakeLibrary.jar", "");
    File updatedSourcePath = createTempFile("updatedFakeLibrary-sources.jar", "");
    File updatedJavadocPath = createTempFile("updatedFakeLibrary-javadoc.jar", "");

    String libraryName = "Gradle: " + cachedBinaryPath.getName();
    Module module = getModule();

    when(myLibraryFilePaths.findSourceJarPath(libraryName, updatedBinaryPath)).thenReturn(updatedSourcePath);
    when(myLibraryFilePaths.findJavadocJarPath(libraryName, updatedBinaryPath)).thenReturn(updatedJavadocPath);

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths = {updatedBinaryPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, updatedBinaryPath, binaryPaths, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.
    LibraryOrderEntry libraryOrderEntry = libraryOrderEntries.get(0);

    // Verify that a new library is created.
    assertNotSame(cachedLibrary, libraryOrderEntry.getLibrary()); // The existing library should have been changed.
    verify(myLibraryFilePaths).findSourceJarPath(libraryName, updatedBinaryPath);
    verify(myLibraryFilePaths).findJavadocJarPath(libraryName, updatedBinaryPath);
  }

  public void testSetUpMultipleLibrariesWithSameName() throws IOException {
    File path1 = createTempFile("fakeLibrary.jar", "");
    File path2 = createTempFile("fakeLibrary2.jar", "");

    String libraryName = "Gradle: fakeLibrary";
    Module module = getModule();

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths1 = {path1};
    File[] binaryPaths2 = {path2};

    // Update library with the same name twice, and make sure there is no "already disposed" exception.
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, path2, binaryPaths1, false);
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, path1, binaryPaths2, false);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.

    Library library = libraryOrderEntries.get(0).getLibrary();
    // Verify that the classpath is the same with the first library.
    assertThat(library.getUrls(CLASSES)).asList().containsExactly(pathToIdeaUrl(path1));
  }
}