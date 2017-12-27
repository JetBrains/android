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

import com.android.tools.idea.gradle.LibraryFilePaths;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

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

    myDependenciesSetup = new AndroidModuleDependenciesSetup(myLibraryFilePaths);
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
    File[] documentationPaths = {javadocPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, binaryPath, binaryPaths, documentationPaths,
                                               false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit); // Apply changes before checking state.

    List<LibraryOrderEntry> libraryOrderEntries = getLibraryOrderEntries(module);
    assertThat(libraryOrderEntries).hasSize(1); // Only one library should be in the library table.
    LibraryOrderEntry libraryOrderEntry = libraryOrderEntries.get(0);
    assertSame(newLibrary, libraryOrderEntry.getLibrary()); // The existing library should not have been changed.

    // Should not attempt to look up sources and documentation for existing libraries.
    verify(myLibraryFilePaths, never()).findSourceJarPath(binaryPath);
    verify(myLibraryFilePaths, never()).findJavadocJarPath(javadocPath);
  }

  @NotNull
  private Library createLibrary(@NotNull File binaryPath, @NotNull File sourcePath, @NotNull File javadocPath) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(getProject());
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
    when(myLibraryFilePaths.findSourceJarPath(binaryPath)).thenReturn(sourcePath);

    String libraryName = "Gradle: " + binaryPath.getName();
    Module module = getModule();

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    File[] binaryPaths = {binaryPath};
    File[] documentationPaths = {javadocPath};
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, libraryName, COMPILE, binaryPath, binaryPaths, documentationPaths,
                                               false);
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

    verify(myLibraryFilePaths).findSourceJarPath(binaryPath);
    // Documentation paths are populated at the LibraryDependency level - no look-up to be done during setup itself
    verify(myLibraryFilePaths, never()).findJavadocJarPath(javadocPath);
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
}