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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleTestArtifactSearchScopesTest extends AndroidGradleTestCase {

  // Naming scheme follows "Gradle: " + name of the library. See LibraryDependency#setName method
  private static final String GRADLE_PREFIX = GradleConstants.SYSTEM_ID.getReadableName() + ": ";

  private static final String GSON = GRADLE_PREFIX + "com.google.code.gson:gson:2.8.0";
  private static final String JUNIT = GRADLE_PREFIX + "junit:junit:4.12";
  @Override
  protected boolean shouldRunTest() {
    if (SystemInfo.isWindows) {
      System.out.println("Class '" + getClass().getName() +
                         "' is skipped because it does not run on Windows (http://b.android.com/222904).");
      return false;
    }
    return super.shouldRunTest();
  }

  public void testPureJavaProject() throws Exception {
    loadProject(PURE_JAVA_PROJECT);

    File srcFile = new File(myFixture.getProject().getBasePath(), toSystemDependentPath("src/main/java/org/gradle/Person.java"));
    assertTrue(srcFile.toString(), srcFile.exists());
    VirtualFile srcVirtualFile = findFileByIoFile(srcFile, true);
    assertNotNull(srcVirtualFile);

    Module mainModule = ModuleUtilCore.findModuleForFile(srcVirtualFile, getProject());
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(mainModule);

    assertFalse(testArtifactSearchScopes.isAndroidTestSource(srcVirtualFile));
  }

  public void testSrcFolderIncluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    VirtualFile unitTestSource = createFileIfNotExists("module1/src/test/java/Test.java");
    VirtualFile androidTestSource = createFileIfNotExists("module1/src/androidTest/java/Test.java");

    assertTrue(scopes.isUnitTestSource(unitTestSource));
    assertFalse(scopes.isUnitTestSource(androidTestSource));

    assertTrue(scopes.isAndroidTestSource(androidTestSource));
    assertFalse(scopes.isAndroidTestSource(unitTestSource));
  }

  public void testProjectRootFolderOfTestProjectType() throws Exception {
    // Module4 is an android test project (applied plugin com.android.test).
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module4");

    VirtualFile module4Root = createFileIfNotExists("module4/src/main");
    VirtualFile module4Source = createFileIfNotExists("module4/src/main/java/Test.java");

    assertFalse(scopes.isUnitTestSource(module4Root));
    assertFalse(scopes.isUnitTestSource(module4Source));

    assertTrue(scopes.isAndroidTestSource(module4Root));
    assertTrue(scopes.isAndroidTestSource(module4Source));
  }

  public void testIncludeLibrariesInUnitTestFromMainModule() throws Exception {
    loadMultiProjectAndGetTestScopesForModule("module1");

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library gson = libraryTable.getLibraryByName(GSON);

    Module module1holderModule = gradleModule(getProject(), ":module1");
    assert module1holderModule != null;
    Module unitTestModule = ModuleSystemUtil.getUnitTestModule(module1holderModule);
    assert unitTestModule != null;

    // In the beginning only androidTest includes the GSON dependency
    Module androidTestModule = ModuleSystemUtil.getAndroidTestModule(module1holderModule);
    assert androidTestModule != null;

    Module mainModule = ModuleSystemUtil.getMainModule(module1holderModule);
    GlobalSearchScope mainModuleModuleWithDependenciesScope = mainModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(mainModuleModuleWithDependenciesScope, gson, false);

    GlobalSearchScope androidTestModuleModuleWithDependenciesScope = androidTestModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(androidTestModuleModuleWithDependenciesScope, gson, true);

    GlobalSearchScope unitTestModuleModuleWithDependenciesScope = unitTestModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(unitTestModuleModuleWithDependenciesScope, gson, false);


    // Now add gson to unit test dependencies as well
    VirtualFile buildFile = getGradleBuildFile(module1holderModule);
    assertNotNull(buildFile);
    appendToFile(virtualToIoFile(buildFile), "\n\ndependencies { api 'com.google.code.gson:gson:2.8.0' }\n");

    CountDownLatch latch = new CountDownLatch(1);
    GradleSyncListener postSetupListener = new GradleSyncListener() {
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
      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
      GradleSyncInvoker.getInstance().requestProjectSync(getProject(), request, null);
    });

    latch.await();

    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.getInstance(module1holderModule);
    assertNotNull(scopes);

    // Now all modules should include GSON as a library dependency
    gson = libraryTable.getLibraryByName(GSON);
    assertScopeContainsLibrary(mainModule.getModuleWithLibrariesScope(), gson, true);
    assertScopeContainsLibrary(androidTestModule.getModuleWithLibrariesScope(), gson, true);
    assertScopeContainsLibrary(unitTestModule.getModuleWithLibrariesScope(), gson, true);
  }

  public void testResolvedScopeForTestOnlyModuleProject() throws Exception {
    loadProject(TEST_ONLY_MODULE);
    Module testModule = TestModuleUtil.findModule(getProject(), "test");
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(testModule);
    assertNotNull(testArtifactSearchScopes);

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());
    Library junit = libraryTable.getLibraryByName(JUNIT);
    assertNotNull(junit);
  }

  @NotNull
  private VirtualFile createFileIfNotExists(@NotNull String relativePath) throws Exception {
    File file = new File(myFixture.getProject().getBasePath(), toSystemDependentPath(relativePath));
    FileUtil.createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertNotNull(virtualFile);
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(getProject());
    return virtualFile;
  }

  @NotNull
  private TestArtifactSearchScopes loadMultiProjectAndGetTestScopesForModule(String moduleName) throws Exception {
    loadProject(SYNC_MULTIPROJECT);
    Module module1 = ModuleSystemUtil.getMainModule(TestModuleUtil.findModule(getProject(), moduleName));
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(module1);
    assertNotNull(testArtifactSearchScopes);
    return testArtifactSearchScopes;
  }

  private static void assertScopeContainsLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library, boolean contains) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertEquals(contains, scope.accept(file));
    }
  }

  public void testGeneratedTestSourcesIncluded() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    // Simulate generated source files. These should be correctly identified as unit or android test.
    VirtualFile unitTestSource = createFileIfNotExists("module1/build/generated/ap_generated_sources/debugUnitTest/out/Test.java");
    VirtualFile androidTestSource = createFileIfNotExists("module1/build/generated/ap_generated_sources/debugAndroidTest/out/Test.java");

    assertTrue(scopes.isUnitTestSource(unitTestSource));
    assertFalse(scopes.isUnitTestSource(androidTestSource));

    assertTrue(scopes.isAndroidTestSource(androidTestSource));
    assertFalse(scopes.isAndroidTestSource(unitTestSource));
  }
 }
