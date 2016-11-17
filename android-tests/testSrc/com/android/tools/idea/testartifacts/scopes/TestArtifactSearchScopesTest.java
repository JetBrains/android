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
package com.android.tools.idea.testartifacts.scopes;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testartifacts.scopes.FileRootSearchScope;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.TestProjectPaths.SHARED_TEST_FOLDER;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.android.utils.FileUtils.join;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class TestArtifactSearchScopesTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    if (SystemInfo.isWindows) {
      System.out.println("Class '" + getClass().getName() +
                         "' is skipped because it does not run on Windows (http://b.android.com/222904).");
      return false;
    }
    return super.shouldRunTest();
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/SrcFolderIncluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile unitTestSource = createFile("module1/src/test/java/Test.java");
    VirtualFile androidTestSource = createFile("module1/src/androidTest/java/Test.java");

    assertTrue(scopes.isUnitTestSource(unitTestSource));
    assertFalse(scopes.isUnitTestSource(androidTestSource));

    assertTrue(scopes.isAndroidTestSource(androidTestSource));
    assertFalse(scopes.isAndroidTestSource(unitTestSource));
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/ModulesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile module3JavaRoot = createFile("module3/src/main/java/Main.java");
    VirtualFile module3RsRoot = createFile("module3/src/main/rs/Main.rs");

    assertTrue(scopes.getUnitTestExcludeScope().accept(module3JavaRoot));
    assertTrue(scopes.getUnitTestExcludeScope().accept(module3RsRoot));

    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3JavaRoot));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3RsRoot));
  }

  // See http://b.android.com/221883.
  public void ignore_testLibrariesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library guava = libraryTable.getLibraryByName("guava-18.0"); // used by android test
    Library hamcrest = libraryTable.getLibraryByName("hamcrest-core-1.3"); // used by both unit and android test
    Library junit = libraryTable.getLibraryByName("junit-4.12");  // used by unit test
    Library gson = libraryTable.getLibraryByName("gson-2.4"); // used by android test

    FileRootSearchScope unitTestExcludeScope = scopes.getUnitTestExcludeScope();
    assertScopeContainsLibrary(unitTestExcludeScope, guava, true);
    assertScopeContainsLibrary(unitTestExcludeScope, gson, true);
    assertScopeContainsLibrary(unitTestExcludeScope, junit, false);
    assertScopeContainsLibrary(unitTestExcludeScope, hamcrest, false);

    FileRootSearchScope androidTestExcludeScope = scopes.getAndroidTestExcludeScope();
    assertScopeContainsLibrary(androidTestExcludeScope, junit, true);
    assertScopeContainsLibrary(androidTestExcludeScope, gson, false);
    assertScopeContainsLibrary(androidTestExcludeScope, guava, false);
    assertScopeContainsLibrary(androidTestExcludeScope, hamcrest, false);
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/NotExcludeLibrariesInMainArtifact() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library gson = libraryTable.getLibraryByName("gson-2.4");
    // In the beginning only unit test exclude gson
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, true);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);

    // Now add gson to unit test dependencies as well
    VirtualFile buildFile = getGradleBuildFile(scopes.getModule());
    assertNotNull(buildFile);
    appendToFile(virtualToIoFile(buildFile), "\n\ndependencies { compile 'com.google.code.gson:gson:2.4' }\n");

    final CountDownLatch latch = new CountDownLatch(1);
    GradleSyncListener postSetupListener = new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        latch.countDown();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        latch.countDown();
      }
    };
    GradleSyncState.subscribe(getProject(), postSetupListener);

    runWriteCommandAction(getProject(), () -> {
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setGenerateSourcesOnSuccess(false);
      GradleSyncInvoker.getInstance().requestProjectSync(getProject(), request, null);
    });

    latch.await();

    // Now both test should not exclude gson
    scopes = TestArtifactSearchScopes.get(scopes.getModule());
    assertNotNull(scopes);
    gson = libraryTable.getLibraryByName("gson-2.4");
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, false);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/ProjectWithSharedTestFolder() throws Exception {
    loadProject(SHARED_TEST_FOLDER);
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(myFixture.getModule());
    assertNotNull(scopes);

    File file = new File(myFixture.getProject().getBasePath(), join("app", "src", "share", "java"));

    assertTrue(scopes.getAndroidTestSourceScope().accept(file));
    assertTrue(scopes.getUnitTestSourceScope().accept(file));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(file));
    assertFalse(scopes.getUnitTestExcludeScope().accept(file));
  }

  @NotNull
  private VirtualFile createFile(@NotNull String relativePath) {
    File file = new File(myFixture.getProject().getBasePath(), toSystemDependentPath(relativePath));
    FileUtil.createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertNotNull(virtualFile);
    return virtualFile;
  }

  @NotNull
  private TestArtifactSearchScopes loadMultiProjectAndTestScopes() throws Exception {
    loadProject(SYNC_MULTIPROJECT);
    Module module1 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("module1");
    assertNotNull(module1);

    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module1);
    assertNotNull(testArtifactSearchScopes);
    return testArtifactSearchScopes;
  }

  private static void assertScopeContainsLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library, boolean contains) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertEquals(contains, scope.accept(file));
    }
  }
}
