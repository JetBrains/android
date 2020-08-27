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
import static com.android.tools.idea.testing.TestProjectPaths.CIRCULAR_MODULE_DEPS;
import static com.android.tools.idea.testing.TestProjectPaths.SHARED_TEST_FOLDER;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleTestArtifactSearchScopesTest extends AndroidGradleTestCase {

  // Naming scheme follows "Gradle: " + name of the library. See LibraryDependency#setName method
  private static final String GRADLE_PREFIX = GradleConstants.SYSTEM_ID.getReadableName() + ": ";

  private static final String GSON = GRADLE_PREFIX + "com.google.code.gson:gson:2.8.0";
  private static final String GUAVA = GRADLE_PREFIX + "com.google.guava:guava:18.0";
  private static final String HAMCREST = GRADLE_PREFIX + "org.hamcrest:hamcrest-core:1.3";
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

    VirtualFile module4Root = createFileIfNotExists("module4");
    VirtualFile module4Source = createFileIfNotExists("module4/src/main/java/Test.java");

    assertFalse(scopes.isUnitTestSource(module4Root));
    assertFalse(scopes.isUnitTestSource(module4Source));

    assertTrue(scopes.isAndroidTestSource(module4Root));
    assertTrue(scopes.isAndroidTestSource(module4Source));
  }

  public void testModulesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    VirtualFile module3JavaRoot = createFileIfNotExists("module3/src/main/java/Main.java");
    VirtualFile module3RsRoot = createFileIfNotExists("module3/src/main/rs/Main.rs");

    assertTrue(scopes.getUnitTestExcludeScope().accept(module3JavaRoot));
    assertTrue(scopes.getUnitTestExcludeScope().accept(module3RsRoot));

    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3JavaRoot));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(module3RsRoot));
  }

  public void testLibrariesExcluding() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library guava = libraryTable.getLibraryByName(GUAVA); // used by android test
    assertNotNull(guava);
    Library hamcrest = libraryTable.getLibraryByName(HAMCREST); // used by android test and sometimes by unit test
    assertNotNull(hamcrest);
    Library junit = libraryTable.getLibraryByName(JUNIT);  // used by unit test
    assertNotNull(junit);
    Library gson = libraryTable.getLibraryByName(GSON); // used by android test
    assertNotNull(gson);

    GlobalSearchScope unitTestExcludeScope = scopes.getUnitTestExcludeScope();
    assertScopeContainsLibrary(unitTestExcludeScope, guava, true);
    assertScopeContainsLibrary(unitTestExcludeScope, gson, true);
    assertScopeContainsLibrary(unitTestExcludeScope, junit, false);

    GlobalSearchScope androidTestExcludeScope = scopes.getAndroidTestExcludeScope();
    assertScopeContainsLibrary(androidTestExcludeScope, junit, true);
    assertScopeContainsLibrary(androidTestExcludeScope, gson, false);
    assertScopeContainsLibrary(androidTestExcludeScope, guava, false);
    assertScopeContainsLibrary(androidTestExcludeScope, hamcrest, false);
  }

  public void testNotExcludeLibrariesInMainArtifact() throws Exception {
    GradleTestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());

    Library gson = libraryTable.getLibraryByName(GSON);
    // In the beginning only unit test exclude gson
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, true);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);

    // Now add gson to unit test dependencies as well
    VirtualFile buildFile = getGradleBuildFile(scopes.getModule());
    assertNotNull(buildFile);
    appendToFile(virtualToIoFile(buildFile), "\n\ndependencies { compile 'com.google.code.gson:gson:2.8.0' }\n");

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
      GradleSyncInvoker.getInstance().requestProjectSync(getProject(), request);
    });

    latch.await();

    // Now both test should not exclude gson
    scopes = GradleTestArtifactSearchScopes.getInstance(scopes.getModule());
    assertNotNull(scopes);
    gson = libraryTable.getLibraryByName(GSON);
    assertScopeContainsLibrary(scopes.getUnitTestExcludeScope(), gson, false);
    assertScopeContainsLibrary(scopes.getAndroidTestExcludeScope(), gson, false);
  }

  public void testProjectWithSharedTestFolder() throws Exception {
    loadProject(SHARED_TEST_FOLDER);
    Module module = TestModuleUtil.findAppModule(getProject());
    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.getInstance(module);
    assertNotNull(scopes);

    VirtualFile file = VfsTestUtil.createDir(ProjectUtil.guessProjectDir(getProject()), "app/src/share/java");

    assertTrue(scopes.getAndroidTestSourceScope().accept(file));
    assertTrue(scopes.getUnitTestSourceScope().accept(file));
    assertFalse(scopes.getAndroidTestExcludeScope().accept(file));
    assertFalse(scopes.getUnitTestExcludeScope().accept(file));
  }

  public void testResolvedScopeForTestOnlyModuleProject() throws Exception {
    loadProject(TEST_ONLY_MODULE);
    Module testModule = TestModuleUtil.findModule(getProject(), "test");
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(testModule);
    assertNotNull(testArtifactSearchScopes);

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myFixture.getProject());
    Library junit = libraryTable.getLibraryByName(JUNIT);
    assertNotNull(junit);
    GlobalSearchScope androidTestExcludeScope = testArtifactSearchScopes.getAndroidTestExcludeScope();
    assertScopeContainsLibrary(androidTestExcludeScope, junit, false);
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
  private GradleTestArtifactSearchScopes loadMultiProjectAndGetTestScopesForModule(String moduleName) throws Exception {
    loadProject(SYNC_MULTIPROJECT);
    Module module1 = TestModuleUtil.findModule(getProject(), moduleName);
    GradleTestArtifactSearchScopes testArtifactSearchScopes = GradleTestArtifactSearchScopes.getInstance(module1);
    assertNotNull(testArtifactSearchScopes);
    return testArtifactSearchScopes;
  }

  private static void assertScopeContainsLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library, boolean contains) {
    assertNotNull(library);
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertEquals(contains, scope.accept(file));
    }
  }

  // See https://issuetracker.google.com/63897699
  public void testCircularModuleDependencies_testUtil() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);

    // verify scope of test-util
    // implementation project(':lib')
    Module testUtilModule = TestModuleUtil.findModule(getProject(), "test-util");
    Module libModule = TestModuleUtil.findModule(getProject(), "lib");
    Module lib2Module = TestModuleUtil.findModule(getProject(), "lib2");

    GradleTestArtifactSearchScopes scopes = GradleTestArtifactSearchScopes.getInstance(testUtilModule);

    Collection<Module> moduleDependencies = getModules(scopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    moduleDependencies = getModules(scopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    moduleDependencies = getModules(scopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    // verify scope of lib
    // testImplementation project(':test-util')
    scopes = GradleTestArtifactSearchScopes.getInstance(libModule);

    moduleDependencies = getModules(scopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).contains(lib2Module);

    moduleDependencies = getModules(scopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(testUtilModule);

    moduleDependencies = getModules(scopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);
  }

  public void testCircularModuleDependencies_lib() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);

    // verify scope of lib
    // testImplementation project(':test-util')
    Module testUtilModule = TestModuleUtil.findModule(getProject(), "test-util");
    Module libModule = TestModuleUtil.findModule(getProject(), "lib");
    Module lib2Module = TestModuleUtil.findModule(getProject(), "lib2");

    GradleTestArtifactSearchScopes scopes = GradleTestArtifactSearchScopes.getInstance(libModule);

    Collection<Module> moduleDependencies = getModules(scopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).contains(lib2Module);

    moduleDependencies = getModules(scopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(testUtilModule);

    moduleDependencies = getModules(scopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    // verify scope of test-util
    // implementation project(':lib')
    scopes = GradleTestArtifactSearchScopes.getInstance(testUtilModule);

    moduleDependencies = getModules(scopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    moduleDependencies = getModules(scopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);

    moduleDependencies = getModules(scopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);
  }

  public void testCircularModuleDependencies_canBuild() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);
    assertThat(invokeGradleTasks(getProject(), "assembleDebug").isBuildSuccessful()).isTrue();
  }

  public void testCircularModuleDependencies_ab() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);

    Module aModule = gradleModule(getProject(), ":a");
    Module bModule = gradleModule(getProject(), ":b");
    Module libModule = gradleModule(getProject(), ":lib");
    Module lib2Module = gradleModule(getProject(), ":lib2");

    GradleTestArtifactSearchScopes aScopes = GradleTestArtifactSearchScopes.getInstance(aModule);

    Collection<Module> moduleDependencies = getModules(aScopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).contains(libModule);
    assertThat(moduleDependencies).contains(lib2Module);

    moduleDependencies = getModules(aScopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(aModule);
    assertThat(moduleDependencies).contains(libModule);
    assertThat(moduleDependencies).contains(lib2Module);

    moduleDependencies = getModules(aScopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(aModule);
    assertThat(moduleDependencies).contains(bModule);
    assertThat(moduleDependencies).contains(libModule);
    assertThat(moduleDependencies).contains(lib2Module);

    GradleTestArtifactSearchScopes bScopes = GradleTestArtifactSearchScopes.getInstance(bModule);

    moduleDependencies = getModules(bScopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).isEmpty();

    moduleDependencies = getModules(bScopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(bModule);

    moduleDependencies = getModules(bScopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(aModule);
    assertThat(moduleDependencies).contains(bModule);
    assertThat(moduleDependencies).contains(libModule);
    assertThat(moduleDependencies).contains(lib2Module);
  }

  public void testCircularModuleDependencies_ab_2() throws Exception {
    loadProject(CIRCULAR_MODULE_DEPS);
    // The second part of the test above (i.e. start resolving from module :b).
    Module aModule = gradleModule(getProject(), ":a");
    Module bModule = gradleModule(getProject(), ":b");
    Module libModule = gradleModule(getProject(), ":lib");
    Module lib2Module = gradleModule(getProject(), ":lib2");

    GradleTestArtifactSearchScopes bScopes = GradleTestArtifactSearchScopes.getInstance(bModule);

    Collection<Module> moduleDependencies = getModules(bScopes.getMainDependencies().onModules());
    assertThat(moduleDependencies).isEmpty();

    moduleDependencies = getModules(bScopes.getUnitTestDependencies().onModules());
    assertThat(moduleDependencies).contains(bModule);

    moduleDependencies = getModules(bScopes.getAndroidTestDependencies().onModules());
    assertThat(moduleDependencies).contains(aModule);
    assertThat(moduleDependencies).contains(bModule);
    assertThat(moduleDependencies).contains(libModule);
    assertThat(moduleDependencies).contains(lib2Module);
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

  private static Collection<Module> getModules(@NotNull Collection<ModuleDependency> deps) {
    return ContainerUtil.map(deps, it -> it.getModule());
  }
}
