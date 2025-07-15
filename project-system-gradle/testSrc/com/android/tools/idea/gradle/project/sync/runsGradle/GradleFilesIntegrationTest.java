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
package com.android.tools.idea.gradle.project.sync.runsGradle;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_CONFIG_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport;
import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.toml.lang.psi.TomlPsiFactory;

/**
 * Tests for {@link GradleFiles}.
 */
public class GradleFilesIntegrationTest extends AndroidGradleTestCase {
  private GradleFiles myGradleFiles;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myGradleFiles = GradleFiles.getInstance(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myGradleFiles = null;
    super.tearDown();
  }

  @Override
  protected File loadSimpleApplication() throws Exception {
    File projectRoot = super.loadSimpleApplication();
    simulateSyncForGradleFilesUpdate();
    return projectRoot;
  }

  private File loadSimpleDeclarativeApplication() throws Exception {
    assertTrue(DeclarativeStudioSupport.isEnabled());
    assertTrue(DeclarativeIdeSupport.isEnabled());
    File file = prepareProjectForImport(TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE);
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(getProjectFolderPath(), true));
    setupGradleSnapshotToWrapper();
    importProject();
    prepareProjectForTest(myFixture.getProject(), null);
    simulateSyncForGradleFilesUpdate();
    return file;
  }

  private void runWithDeclarativeSupport(ThrowableRunnable<Exception> runnable) throws Exception {
    try {
      DeclarativeStudioSupport.override(true);
      DeclarativeIdeSupport.override(true);
      runnable.run();
    }
    finally {
      DeclarativeIdeSupport.clearOverride();
      DeclarativeStudioSupport.clearOverride();
    }
  }

  private void simulateSyncForGradleFilesUpdate() {
    myGradleFiles.maybeProcessSyncStarted();
    myGradleFiles.maybeProcessSyncSucceeded();
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(myGradleFiles.areGradleFilesModified());
  }

  private void setupGradleSnapshotToWrapper() throws IOException {
    Path distribution = TestUtils.resolveWorkspacePath("tools/external/gradle");
    Path gradle = distribution.resolve("gradle-8.14-20250304001707+0000-bin.zip");
    GradleWrapper wrapper = GradleWrapper.find(myFixture.getProject());
    wrapper.updateDistributionUrl(gradle.toFile());
  }

  public void testNotModifiedWhenAddingWhitespaceInBuildFile() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false);
  }

  public void testNotModifiedWhenAddingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> {
      PsiFile dummyFile = factory.createGroovyFile("// foo", false, null);
      PsiElement comment = dummyFile.getFirstChild();
      file.add(comment);
    }, false);
  }

  public void testNotModifiedWhenEditingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + 3, "abc");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, false, buildFile);
  }

  public void testNotModifiedWhenAddingNewlineCommentToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n//");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, false, buildFile);
  }

  public void testNotModifiedWhenRemovingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }, false, buildFile);
  }

  public void testModifiedWhenDeletingBringsProgramToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      // This is fragile, but at least the assertions below will catch modifications to build.gradle which would invalidate this test
      PsiElement buildscript = file.findElementAt(101);
      assertThat(buildscript.getTextOffset()).isEqualTo(101);
      assertThat(buildscript.getText()).isEqualTo("buildscript");
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.deleteString(3, 101);
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, true, buildFile);
  }

  public void testModifiedWhenDeletingCommentCharacters() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.deleteString(1, 2);
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, true, buildFile);
  }

  public void testModifiedWhenAddingNewlineToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, true, buildFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  public void testModifiedWhenAddingTextInGradleConfigPropertiesFile() throws Exception {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(".gradle", FN_GRADLE_CONFIG_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("test.property=true")), true,
                                  virtualFile);
  }

  public void testNotModifiedWhenAddingTextInGradleConfigPropertiesFileOutsideOfCacheDir() throws Exception {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(".not-gradle", FN_GRADLE_CONFIG_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("test.property=true")), false,
                                  virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.find(project);
    assertNotNull(wrapper);
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.find(project);
    assertNotNull(wrapper);
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  public void testModifiedWhenReplacingChild() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }), true);
  }

  public void testModifiedWhenChildRemoved() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getFirstChild() instanceof PsiComment).isFalse();
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }), true);
  }

  public void testNotModifiedWhenInnerWhiteSpaceIsAdded() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      element.addAfter(factory.createWhiteSpace(), element.getLastChild());
    }), false);
  }

  public void testNotModifiedWhenInnerNewLineIsAdded() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      file.addAfter(factory.createLineTerminator("\n   \t\t\n "), element);
    }), false);
  }

  public void testNotModifiedWhenTextIsIdentical() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }), true);
    runGroovyFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: \"com.android.application\"");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.android.application'"));
    }), false, false, getAppBuildFile());
  }

  public void testModifiedWhenDeleteAfterSync() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }), true);
    simulateSyncForGradleFilesUpdate();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: \"com.android.application\"");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.android.application'"));
    }), true);
  }

  public void testNotModifiedAfterSync() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }, true);
    simulateSyncForGradleFilesUpdate();
    assertThat(myGradleFiles.areGradleFilesModified()).isFalse();
  }

  public void testNotModifiedWhenChangedBackDuringSync() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }), true);
    simulateSyncForGradleFilesUpdate();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: \"com.android.application\"");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.android.application'"));
    }, true);
    runGroovyFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }), false, false, getAppBuildFile());
  }

  public void testModifiedWhenVersionCatalogFileChanged() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG);
    simulateSyncForGradleFilesUpdate();
    VirtualFile libs = findOrCreateFileRelativeToProjectRootFolder("gradle", "libs.versions.toml");
    runTomlFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      PsiElement firstCopy = file.getChildren()[0].copy();
      file.getChildren()[0].add(firstCopy);
    }, true, libs);
  }

  // Ensures hashes are not stored if the File does not exist.
  public void testNoPhysicalFileExists() throws Exception {
    loadSimpleApplication();
    File path = VfsUtilCore.virtualToIoFile(getAppBuildFile());
    boolean deleted = path.delete();
    assertThat(deleted).isTrue();
    assertThat(getAppBuildFile().exists()).isTrue();
    simulateSyncForGradleFilesUpdate();
    assertThat(myGradleFiles.areGradleFilesModified()).isFalse();
    assertThat(myGradleFiles.hasHashForFile(getAppBuildFile())).isFalse();
  }

  public void testCommentingOutTriggersModification() throws Exception {
    loadSimpleApplication();

    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      PsiElement element = ProjectBuildModelHandler.Companion.getInstance(getProject()).read((model) -> {
        List<DependencyModel> dependencies = model.getModuleBuildModel(getModule("app")).dependencies().all();
        assertThat(dependencies.size()).isGreaterThan(0);
        return dependencies.get(0).getPsiElement();
      }).getParent();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(element.getContainingFile());
      doc.insertString(element.getTextOffset(), "//");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }), true);
  }

  public void testModifiedWhenAddingTextChildInBuildFile() throws Exception {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true);
  }

  public void testModifiedWhenAddingTextChildInSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  public void testModifiedWhenAddingTextChildInDeclarativeSettingsFile() throws Exception {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createBlock("coolBlock")), true,
                                         virtualFile);
    });
  }

  public void testModifiedWhenAddingTextChildInDeclarativeBuildFile() throws Exception {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder("app", FN_BUILD_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createBlock("coolBlock")), true,
                                         virtualFile);
    });
  }

  public void testModifiedWhenAddingTextChildInKotlinBuildFile() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    simulateSyncForGradleFilesUpdate();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createProperty("val coolexpression by extra(\"nice!\")")),
                               true, true, virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInSettingsFile() throws Exception {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInKotlinSettingsFile() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createNewLine(1)), false, virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInDeclarativeSettingsFile() throws Exception {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createNewline()), false, virtualFile);
    });
  }

  public void testNotModifiedWhenAddingWhitespaceInKotlinBuildFile() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createNewLine(1)), false, virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInDeclarativeBuildFile() throws Exception {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder("app", FN_BUILD_GRADLE_DECLARATIVE);
      runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
    });
  }

  @NotNull
  private VirtualFile getAppBuildFile() {
    Module appModule = TestModuleUtil.findAppModule(getProject());
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);
    return buildFile;
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).findFile(file);
    assertNotNull(psiFile);
    return psiFile;
  }

  @NotNull
  private VirtualFile findOrCreateFileRelativeToProjectRootFolder(@NotNull String... names) {
    File filePath = findOrCreateFilePathRelativeToProjectRootFolder(names);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull(file);
    return file;
  }

  private @NotNull File findOrCreateFilePathRelativeToProjectRootFolder(@NotNull String... names) {
    File parent = getBaseDirPath(getProject());
    for (int i = 0; i < names.length - 1; i++) {
      File child = new File(parent, names[i]);
      if (!child.exists()) {
        assertTrue(child.mkdirs());
      }
      parent = child;
    }
    File result = new File(parent, names[names.length - 1]);
    assertTrue(createIfNotExists(result));
    return result;
  }

  private void runGroovyAppBuildFileFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                                         boolean expectedResult) {
    runGroovyFakeModificationTest(editFunction, expectedResult, true, getAppBuildFile());
  }

  private void runGroovyFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                             boolean expectedResult,
                                             @NotNull VirtualFile file) {
    runGroovyFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void runGroovyFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                             boolean expectedResult,
                                             boolean preCheckEnabled,
                                             @NotNull VirtualFile file) {
    runGenericFakeModificationTest(GroovyPsiElementFactory::getInstance, editFunction, expectedResult, preCheckEnabled, file);
  }

  private void runKtsFakeModificationTest(@NotNull BiConsumer<KtPsiFactory, PsiFile> editFunction,
                                          boolean expectedResult,
                                          @NotNull VirtualFile file) {
    runKtsFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void runKtsFakeModificationTest(@NotNull BiConsumer<KtPsiFactory, PsiFile> editFunction,
                                          boolean expectedResult,
                                          boolean preCheckEnabled,
                                          @NotNull VirtualFile file) {
    runGenericFakeModificationTest(KtPsiFactory::new, editFunction, expectedResult, preCheckEnabled, file);
  }

  private void runDeclarativeFakeModificationTest(@NotNull BiConsumer<DeclarativePsiFactory, PsiFile> editFunction,
                                                  boolean expectedResult,
                                                  @NotNull VirtualFile file) {
    runDeclarativeFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void runDeclarativeFakeModificationTest(@NotNull BiConsumer<DeclarativePsiFactory, PsiFile> editFunction,
                                                  boolean expectedResult,
                                                  boolean preCheckEnabled,
                                                  @NotNull VirtualFile file) {
    assertTrue(DeclarativeStudioSupport.isEnabled());
    assertTrue(DeclarativeIdeSupport.isEnabled());
    runGenericFakeModificationTest(DeclarativePsiFactory::new, editFunction, expectedResult, preCheckEnabled, file);
  }

  private void runTomlFakeModificationTest(@NotNull BiConsumer<TomlPsiFactory, PsiFile> editFunction,
                                           boolean expectedResult,
                                           @NotNull VirtualFile file) {
    runTomlFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void runTomlFakeModificationTest(@NotNull BiConsumer<TomlPsiFactory, PsiFile> editFunction,
                                           boolean expectedResult,
                                           boolean preCheckEnabled,
                                           @NotNull VirtualFile file) {
    runGenericFakeModificationTest((project) -> new TomlPsiFactory(project, true), editFunction, expectedResult, preCheckEnabled, file);
  }

  private <T> void runGenericFakeModificationTest(
    @NotNull Function<Project, T> factoryFactory,
    @NotNull BiConsumer<T, PsiFile> editFunction,
    boolean expectedResult,
    boolean preCheckEnabled,
    @NotNull VirtualFile file
  ) {
    PsiFile psiFile = findPsiFile(file);

    T factory = factoryFactory.apply(getProject());

    boolean filesModified = myGradleFiles.areGradleFilesModified();
    if (preCheckEnabled) {
      assertFalse(filesModified);
    }

    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        editFunction.accept(factory, psiFile);
        psiFile.subtreeChanged();
      }), "Fake Edit Test", null);

    filesModified = myGradleFiles.areGradleFilesModified();
    if (expectedResult) {
      assertTrue(filesModified);
    }
    else {
      assertFalse(filesModified);
    }
  }
}