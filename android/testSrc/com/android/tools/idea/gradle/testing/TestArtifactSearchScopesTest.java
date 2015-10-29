/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.testing;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class TestArtifactSearchScopesTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = true;
  }

  public void testSrcFolderIncluding() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("TestArtifactSearchScopesTest.testSrcFolderIncluding temporarily disabled");
      return;
    }
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile unitTestSource = createFile("module1/src/test/java/Test.java");
    VirtualFile androidTestSource = createFile("module1/src/androidTest/java/Test.java");

    assertTrue(scopes.isUnitTestSource(unitTestSource));
    assertFalse(scopes.isUnitTestSource(androidTestSource));

    assertTrue(scopes.isAndroidTestSource(androidTestSource));
    assertFalse(scopes.isAndroidTestSource(unitTestSource));
  }

  public void testModulesExcluding() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("TestArtifactSearchScopesTest.testModulesExcluding temporarily disabled");
      return;
    }
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile module3JavaRoot = createFile("module3/src/main/java/Main.java");
    VirtualFile module3RsRoot = createFile("module3/src/main/rs/Main.rs");

    assertTrue(scopes.getUnitTestExcludeScope().accept(module3JavaRoot));
    assertTrue(scopes.getUnitTestExcludeScope().accept(module3RsRoot));

    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3JavaRoot));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3RsRoot));
  }

  public void testLibrariesExcluding() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("TestArtifactSearchScopesTest.testLibrariesExcluding temporarily disabled");
      return;
    }

    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library guava = libraryTable.getLibraryByName("guava-18.0"); // used by android test
    Library hamcrest = libraryTable.getLibraryByName("hamcrest-core-1.3"); // used by both unit and android test
    Library junit = libraryTable.getLibraryByName("junit-4.12");  // used by unit test
    Library gson = libraryTable.getLibraryByName("gson-2.4"); // used by android test

    assertAcceptLibrary(scopes.getUnitTestExcludeScope(), guava);
    assertAcceptLibrary(scopes.getUnitTestExcludeScope(), gson);
    assertNotAcceptLibrary(scopes.getUnitTestExcludeScope(), junit);
    assertNotAcceptLibrary(scopes.getUnitTestExcludeScope(), hamcrest);

    assertAcceptLibrary(scopes.getAndroidTestExcludeScope(), junit);
    assertNotAcceptLibrary(scopes.getAndroidTestExcludeScope(), gson);
    assertNotAcceptLibrary(scopes.getAndroidTestExcludeScope(), guava);
    assertNotAcceptLibrary(scopes.getAndroidTestExcludeScope(), hamcrest);
  }

  @NotNull
  private VirtualFile createFile(@NotNull String relativePath) {
    File file = new File(myFixture.getProject().getBasePath(), relativePath);
    FileUtil.createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertNotNull(virtualFile);
    return virtualFile;
  }

  @NotNull
  private TestArtifactSearchScopes loadMultiProjectAndTestScopes() throws Exception {
    loadProject("projects/sync/multiproject", false);
    Module module1 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("module1");
    assertNotNull(module1);

    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module1);
    assertNotNull(testArtifactSearchScopes);
    return testArtifactSearchScopes;
  }

  private static void assertAcceptLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertTrue(scope.accept(file));
    }
  }

  private static void assertNotAcceptLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertFalse(scope.accept(file));
    }
  }
}
