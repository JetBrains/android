/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes.runsGradle;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.projectsystem.gradle.LinkedAndroidModuleGroupUtilsKt;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RunsInEdt
public class GradleTestArtifactSearchScopesTest {

  // Naming scheme follows "Gradle: " + name of the library. See LibraryDependency#setName method
  private static final String GRADLE_PREFIX = GradleConstants.SYSTEM_ID.getReadableName() + ": ";

  private static final String GSON = GRADLE_PREFIX + "com.google.code.gson:gson:2.8.0";
  private static final String JUNIT = GRADLE_PREFIX + "junit:junit:4.12";

  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public RuleChain rule = RuleChain.outerRule(new EdtRule()).around(projectRule);

  @Test
  public void testPureJavaProject() throws Exception {
    projectRule.loadProject(PURE_JAVA_PROJECT);

    File srcFile = new File(projectRule.getProject().getBasePath(), toSystemDependentPath("src/main/java/org/gradle/Person.java"));
    assertThat(srcFile.exists()).named(srcFile.toString()).isTrue();
    VirtualFile srcVirtualFile = findFileByIoFile(srcFile, true);
    assertThat(srcVirtualFile).isNotNull();

    Module mainModule = ModuleUtilCore.findModuleForFile(srcVirtualFile, projectRule.getProject());
    assertThat(mainModule).isNotNull();
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(mainModule);

    assertThat(testArtifactSearchScopes.isAndroidTestSource(srcVirtualFile)).isFalse();
  }

  @Test
  public void testSrcFolderIncluding() throws Exception {
    projectRule.loadProject(SIMPLE_APP_WITH_SCREENSHOT_TEST);
    Module module1 = LinkedAndroidModuleGroupUtilsKt.getMainModule(TestModuleUtil.findAppModule(projectRule.getProject()));
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(module1);
    assertThat(testArtifactSearchScopes).isNotNull();

    VirtualFile unitTestSource = createFileIfNotExists("app/src/test/java/Test.java");
    VirtualFile androidTestSource = createFileIfNotExists("app/src/androidTest/java/Test.java");
    VirtualFile screenshotTestSource = createFileIfNotExists("app/src/screenshotTest/java/Test.java");

    assertThat(testArtifactSearchScopes.isUnitTestSource(unitTestSource)).isTrue();
    assertThat(testArtifactSearchScopes.isUnitTestSource(androidTestSource)).isFalse();
    assertThat(testArtifactSearchScopes.isUnitTestSource(screenshotTestSource)).isFalse();

    assertThat(testArtifactSearchScopes.isAndroidTestSource(androidTestSource)).isTrue();
    assertThat(testArtifactSearchScopes.isAndroidTestSource(unitTestSource)).isFalse();
    assertThat(testArtifactSearchScopes.isAndroidTestSource(screenshotTestSource)).isFalse();

    assertThat(testArtifactSearchScopes.isScreenshotTestSource(unitTestSource)).isFalse();
    assertThat(testArtifactSearchScopes.isScreenshotTestSource(androidTestSource)).isFalse();
    assertThat(testArtifactSearchScopes.isScreenshotTestSource(screenshotTestSource)).isTrue();
  }

  @Test
  public void testProjectRootFolderOfTestProjectType() throws Exception {
    // Module4 is an android test project (applied plugin com.android.test).
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module4");

    VirtualFile module4Root = createFileIfNotExists("module4/src/main");
    VirtualFile module4Source = createFileIfNotExists("module4/src/main/java/Test.java");

    assertThat(scopes.isUnitTestSource(module4Root)).isFalse();
    assertThat(scopes.isUnitTestSource(module4Source)).isFalse();

    assertThat(scopes.isAndroidTestSource(module4Root)).isTrue();
    assertThat(scopes.isAndroidTestSource(module4Source)).isTrue();
  }

  @Test
  public void testIncludeLibrariesInUnitTestFromMainModule() throws Exception {
    loadMultiProjectAndGetTestScopesForModule("module1");

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectRule.getProject());

    Library gson = libraryTable.getLibraryByName(GSON);

    Module module1holderModule = gradleModule(projectRule.getProject(), ":module1");
    assert module1holderModule != null;
    Module unitTestModule = LinkedAndroidModuleGroupUtilsKt.getUnitTestModule(module1holderModule);
    assert unitTestModule != null;

    // In the beginning only androidTest includes the GSON dependency
    Module androidTestModule = LinkedAndroidModuleGroupUtilsKt.getAndroidTestModule(module1holderModule);
    assert androidTestModule != null;

    Module mainModule = LinkedAndroidModuleGroupUtilsKt.getMainModule(module1holderModule);
    GlobalSearchScope mainModuleModuleWithDependenciesScope = mainModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(mainModuleModuleWithDependenciesScope, gson, false);

    GlobalSearchScope androidTestModuleModuleWithDependenciesScope = androidTestModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(androidTestModuleModuleWithDependenciesScope, gson, true);

    GlobalSearchScope unitTestModuleModuleWithDependenciesScope = unitTestModule.getModuleWithLibrariesScope();
    assertScopeContainsLibrary(unitTestModuleModuleWithDependenciesScope, gson, false);


    // Now add gson to unit test dependencies as well
    VirtualFile buildFile = getGradleBuildFile(module1holderModule);
    assertThat(buildFile).isNotNull();
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
    GradleSyncState.subscribe(projectRule.getProject(), postSetupListener, projectRule.getFixture().getTestRootDisposable());
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
    GradleSyncInvoker.getInstance().requestProjectSync(projectRule.getProject(), request, null);

    latch.await();

    TestArtifactSearchScopes scopes = TestArtifactSearchScopes.getInstance(module1holderModule);
    assertThat(scopes).isNotNull();

    // Now all modules should include GSON as a library dependency
    gson = libraryTable.getLibraryByName(GSON);
    assertScopeContainsLibrary(mainModule.getModuleWithLibrariesScope(), gson, true);
    assertScopeContainsLibrary(androidTestModule.getModuleWithLibrariesScope(), gson, true);
    assertScopeContainsLibrary(unitTestModule.getModuleWithLibrariesScope(), gson, true);
  }

  @Test
  public void testResolvedScopeForTestOnlyModuleProject() throws Exception {
    projectRule.loadProject(TEST_ONLY_MODULE);
    Module testModule = TestModuleUtil.findModule(projectRule.getProject(), "test");
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(testModule);
    assertThat(testArtifactSearchScopes).isNotNull();

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectRule.getProject());
    Library junit = libraryTable.getLibraryByName(JUNIT);
    assertThat(junit).isNotNull();
  }

  @Test
  public void testGeneratedTestSourcesIncluded() throws Exception {
    TestArtifactSearchScopes scopes = loadMultiProjectAndGetTestScopesForModule("module1");

    // Simulate generated source files. These should be correctly identified as unit or android test.
    VirtualFile unitTestSource = createFileIfNotExists("module1/build/generated/ap_generated_sources/debugUnitTest/out/Test.java");
    VirtualFile androidTestSource = createFileIfNotExists("module1/build/generated/ap_generated_sources/debugAndroidTest/out/Test.java");

    assertThat(scopes.isUnitTestSource(unitTestSource)).isTrue();
    assertThat(scopes.isUnitTestSource(androidTestSource)).isFalse();

    assertThat(scopes.isAndroidTestSource(androidTestSource)).isTrue();
    assertThat(scopes.isAndroidTestSource(unitTestSource)).isFalse();
  }

  @NotNull
  private VirtualFile createFileIfNotExists(@NotNull String relativePath) throws Exception {
    File file = new File(projectRule.getProject().getBasePath(), toSystemDependentPath(relativePath));
    FileUtil.createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertThat(virtualFile).isNotNull();
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(projectRule.getProject());
    return virtualFile;
  }

  @NotNull
  private TestArtifactSearchScopes loadMultiProjectAndGetTestScopesForModule(String moduleName) throws Exception {
    projectRule.loadProject(SYNC_MULTIPROJECT);
    Module module1 = LinkedAndroidModuleGroupUtilsKt.getMainModule(TestModuleUtil.findModule(projectRule.getProject(), moduleName));
    TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(module1);
    assertThat(testArtifactSearchScopes).isNotNull();
    return testArtifactSearchScopes;
  }

  private static void assertScopeContainsLibrary(@NotNull GlobalSearchScope scope, @Nullable Library library, boolean contains) {
    assertThat(library).isNotNull();
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      assertThat(scope.accept(file)).isEqualTo(contains);
    }
  }
}
