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

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableCollection;
import com.intellij.openapi.module.Module;
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
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.android.utils.FileUtils.join;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class TestArtifactSearchScopesTest extends AndroidGradleTestCase {

  private static final String GSON = "com.google.code.gson:gson:2.2.4@jar";
  private static final String GUAVA = "com.google.guava:guava:18.0@jar";
  private static final String HAMCREST = "org.hamcrest:hamcrest-core:1.3@jar";
  private static final String JUNIT = "junit:junit:4.12@jar";
  @Override
  protected boolean shouldRunTest() {
    if (SystemInfo.isWindows) {
      System.out.println("Class '" + getClass().getName() +
                         "' is skipped because it does not run on Windows (http://b.android.com/222904).");
      return false;
    }
    return super.shouldRunTest();
  }

  public void testSrcFolderIncluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile unitTestSource = createFile("module1/src/test/java/Test.java");
    VirtualFile androidTestSource = createFile("module1/src/androidTest/java/Test.java");

    assertTrue(scopes.isUnitTestSource(unitTestSource));
    assertFalse(scopes.isUnitTestSource(androidTestSource));

    assertTrue(scopes.isAndroidTestSource(androidTestSource));
    assertFalse(scopes.isAndroidTestSource(unitTestSource));
  }

  public void testModulesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    VirtualFile module3JavaRoot = createFile("module3/src/main/java/Main.java");
    VirtualFile module3RsRoot = createFile("module3/src/main/rs/Main.rs");

    assertTrue(scopes.getUnitTestExcludeScope().accept(module3JavaRoot));
    assertTrue(scopes.getUnitTestExcludeScope().accept(module3RsRoot));

    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3JavaRoot));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3RsRoot));
  }

  public void testLibrariesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library guava = libraryTable.getLibraryByName(GUAVA); // used by android test
    assertNotNull(guava);
    Library hamcrest = libraryTable.getLibraryByName(HAMCREST); // used by android test and sometimes by unit test
    assertNotNull(hamcrest);
    Library junit = libraryTable.getLibraryByName(JUNIT);  // used by unit test
    assertNotNull(junit);
    Library gson = libraryTable.getLibraryByName(GSON); // used by android test
    assertNotNull(gson);

    FileRootSearchScope unitTestExcludeScope = scopes.getUnitTestExcludeScope();
    assertScopeContainsLibrary(unitTestExcludeScope, guava, true);
    assertScopeContainsLibrary(unitTestExcludeScope, gson, true);
    assertScopeContainsLibrary(unitTestExcludeScope, junit, false);

    FileRootSearchScope androidTestExcludeScope = scopes.getAndroidTestExcludeScope();
    assertScopeContainsLibrary(androidTestExcludeScope, junit, true);
    assertScopeContainsLibrary(androidTestExcludeScope, gson, false);
    assertScopeContainsLibrary(androidTestExcludeScope, guava, false);
    assertScopeContainsLibrary(androidTestExcludeScope, hamcrest, false);
  }

  public void testNotExcludeLibrariesInMainArtifact() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndTestScopes();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library gson = libraryTable.getLibraryByName(GSON);
    // In the beginning only unit test exclude gson
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, true);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);

    // Now add gson to unit test dependencies as well
    VirtualFile buildFile = getGradleBuildFile(scopes.getModule());
    assertNotNull(buildFile);
    appendToFile(virtualToIoFile(buildFile), "\n\ndependencies { compile 'com.google.code.gson:gson:2.2.4' }\n");

    CountDownLatch latch = new CountDownLatch(1);
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
    gson = libraryTable.getLibraryByName(GSON);
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, false);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);
  }

  public void testProjectWithSharedTestFolder() throws Exception {
    loadProject(SHARED_TEST_FOLDER);
    Module module = myModules.getAppModule();
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
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
    Module module1 = myModules.getModule("module1");
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(module1);
    assertNotNull(testArtifactSearchScopes);
    return testArtifactSearchScopes;
  }

  public void testMergeSubmoduleDependencies() throws Exception {
    loadProject(TEST_ARTIFACTS_MULTIDEPENDENCIES);
    Module module = myModules.getModule("module1");
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
    scopes.resolveDependencies();
  }

  private static void assertScopeContainsLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library, boolean contains) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertEquals(contains, scope.accept(file));
    }
  }

  // See https://issuetracker.google.com/63897699
  public void testCircularModuleDependencies() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);

    // verify scope of test-util
    // implementation project(':lib')
    Module module = myModules.getModule("test-util");
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
    scopes.resolveDependencies();

    ImmutableCollection<ModuleDependency> moduleDependencies = scopes.getMainDependencies().onModules();
    assertThat(moduleDependencies).contains(new ModuleDependency(":lib", COMPILE));

    moduleDependencies = scopes.getUnitTestDependencies().onModules();
    assertThat(moduleDependencies).contains(new ModuleDependency(":lib", COMPILE));

    moduleDependencies = scopes.getAndroidTestDependencies().onModules();
    assertThat(moduleDependencies).contains(new ModuleDependency(":lib", TEST));

    // verify scope of lib
    // testImplementation project(':test-util')
    module = myModules.getModule("lib");
    scopes = TestArtifactSearchScopes.get(module);
    scopes.resolveDependencies();

    moduleDependencies = scopes.getMainDependencies().onModules();
    assertThat(moduleDependencies).isEmpty();

    moduleDependencies = scopes.getUnitTestDependencies().onModules();
    assertThat(moduleDependencies).contains(new ModuleDependency(":test-util", TEST));

    moduleDependencies = scopes.getAndroidTestDependencies().onModules();
    assertThat(moduleDependencies).isEmpty();
  }
}
