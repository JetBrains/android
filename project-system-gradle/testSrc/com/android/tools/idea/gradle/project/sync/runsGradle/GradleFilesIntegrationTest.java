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

import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport;
import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.toml.lang.psi.TomlPsiFactory;

/**
 * Tests for {@link GradleFiles}.
 */
@RunsInEdt
public class GradleFilesIntegrationTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public RuleChain rule = RuleChain.outerRule(projectRule).around(new EdtRule());

  private GradleFiles myGradleFiles;

  @Before
  public void setup() {
    myGradleFiles = GradleFiles.getInstance(projectRule.getProject());
  }

  @After
  public void teardown() {
    myGradleFiles = null;
  }

  private void loadSimpleApplication() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION);
    simulateSyncForGradleFilesUpdate();
  }

  private void loadSimpleDeclarativeApplication() {
    assertThat(DeclarativeStudioSupport.isEnabled()).isTrue();
    assertThat(DeclarativeIdeSupport.isEnabled()).isTrue();
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE);
    simulateSyncForGradleFilesUpdate();
  }

  private void runWithDeclarativeSupport(Runnable runnable) {
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
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    assertThat(myGradleFiles.areGradleFilesModified()).isFalse();
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInBuildFile() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false);
  }

  @Test
  public void testNotModifiedWhenAddingCommentInBuildFile() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> {
      PsiFile dummyFile = factory.createGroovyFile("// foo", false, null);
      PsiElement comment = dummyFile.getFirstChild();
      file.add(comment);
    }, false);
  }

  @Test
  public void testNotModifiedWhenEditingCommentInBuildFile() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    Project project = projectRule.getProject();

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + 3, "abc");
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }, false, buildFile);
  }

  @Test
  public void testNotModifiedWhenAddingNewlineCommentToCommentInBuildFile() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    Project project = projectRule.getProject();

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n//");
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }, false, buildFile);
  }

  @Test
  public void testNotModifiedWhenRemovingCommentInBuildFile() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }, false, buildFile);
  }

  @Test
  public void testModifiedWhenDeletingBringsProgramToCommentInBuildFile() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    Project project = projectRule.getProject();

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      // This is fragile, but at least the assertions below will catch modifications to build.gradle which would invalidate this test
      PsiElement buildscript = file.findElementAt(101);
      assertThat(buildscript.getTextOffset()).isEqualTo(101);
      assertThat(buildscript.getText()).isEqualTo("buildscript");
      Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      doc.deleteString(3, 101);
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }, true, buildFile);
  }

  @Test
  public void testModifiedWhenDeletingCommentCharacters() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    Project project = projectRule.getProject();

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      doc.deleteString(1, 2);
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }, true, buildFile);
  }

  @Test
  public void testModifiedWhenAddingNewlineToCommentInBuildFile() {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    Project project = projectRule.getProject();

    runGroovyFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n");
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }, true, buildFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInPropertiesFile() {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  @Test
  public void testModifiedWhenAddingTextChildInPropertiesFile() {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  @Test
  public void testModifiedWhenAddingTextInGradleConfigPropertiesFile() {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(".gradle", FN_GRADLE_CONFIG_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("test.property=true")), true,
                                  virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingTextInGradleConfigPropertiesFileOutsideOfCacheDir() {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(".not-gradle", FN_GRADLE_CONFIG_PROPERTIES);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("test.property=true")), false,
                                  virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInWrapperPropertiesFile() {
    loadSimpleApplication();
    Project project = projectRule.getProject();
    GradleWrapper wrapper = GradleWrapper.find(project);
    assertThat(wrapper).isNotNull();
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  @Test
  public void testModifiedWhenAddingTextChildInWrapperPropertiesFile() {
    loadSimpleApplication();
    Project project = projectRule.getProject();
    GradleWrapper wrapper = GradleWrapper.find(project);
    assertThat(wrapper).isNotNull();
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  @Test
  public void testModifiedWhenReplacingChild() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }), true);
  }

  @Test
  public void testModifiedWhenChildRemoved() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getFirstChild() instanceof PsiComment).isFalse();
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }), true);
  }

  @Test
  public void testNotModifiedWhenInnerWhiteSpaceIsAdded() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      element.addAfter(factory.createWhiteSpace(), element.getLastChild());
    }), false);
  }

  @Test
  public void testNotModifiedWhenInnerNewLineIsAdded() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      file.addAfter(factory.createLineTerminator("\n   \t\t\n "), element);
    }), false);
  }

  @Test
  public void testNotModifiedWhenTextIsIdentical() {
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

  @Test
  public void testModifiedWhenDeleteAfterSync() {
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

  @Test
  public void testNotModifiedAfterSync() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      assertThat(file.getChildren()[0].getText()).isEqualTo("apply plugin: 'com.android.application'");
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: \"com.android.application\""));
    }, true);
    simulateSyncForGradleFilesUpdate();
    assertThat(myGradleFiles.areGradleFilesModified()).isFalse();
  }

  @Test
  public void testNotModifiedWhenChangedBackDuringSync() {
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

  @Test
  public void testModifiedWhenVersionCatalogFileChanged() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG);
    simulateSyncForGradleFilesUpdate();
    VirtualFile libs = findOrCreateFileRelativeToProjectRootFolder("gradle", "libs.versions.toml");
    runTomlFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      PsiElement firstCopy = file.getChildren()[0].copy();
      file.getChildren()[0].add(firstCopy);
    }, true, libs);
  }

  // Ensures hashes are not stored if the File does not exist.
  @Test
  public void testNoPhysicalFileExists() {
    loadSimpleApplication();
    File path = VfsUtilCore.virtualToIoFile(getAppBuildFile());
    boolean deleted = path.delete();
    assertThat(deleted).isTrue();
    assertThat(getAppBuildFile().exists()).isTrue();
    simulateSyncForGradleFilesUpdate();
    assertThat(myGradleFiles.areGradleFilesModified()).isFalse();
    assertThat(myGradleFiles.hasHashForFile(getAppBuildFile())).isFalse();
  }

  @Test
  public void testCommentingOutTriggersModification() {
    loadSimpleApplication();
    Project project = projectRule.getProject();

    runGroovyAppBuildFileFakeModificationTest(((factory, file) -> {
      PsiElement element = ProjectBuildModelHandler.Companion.getInstance(project).read((model) -> {
        List<DependencyModel> dependencies = model.getModuleBuildModel(projectRule.getModule("app")).dependencies().all();
        assertThat(dependencies.size()).isGreaterThan(0);
        return dependencies.get(0).getPsiElement();
      }).getParent();
      Document doc = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
      doc.insertString(element.getTextOffset(), "//");
      PsiDocumentManager.getInstance(project).commitDocument(doc);
    }), true);
  }

  @Test
  public void testModifiedWhenAddingTextChildInBuildFile() {
    loadSimpleApplication();
    runGroovyAppBuildFileFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true);
  }

  @Test
  public void testModifiedWhenAddingTextChildInSettingsFile() {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                                  virtualFile);
  }

  @Test
  public void testModifiedWhenAddingTextChildInDeclarativeSettingsFile() {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createBlock("coolBlock")), true,
                                         virtualFile);
    });
  }

  @Test
  public void testModifiedWhenAddingTextChildInDeclarativeBuildFile() {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder("app", FN_BUILD_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createBlock("coolBlock")), true,
                                         virtualFile);
    });
  }

  @Test
  public void testModifiedWhenAddingTextChildInKotlinBuildFile() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    simulateSyncForGradleFilesUpdate();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createProperty("val coolexpression by extra(\"nice!\")")),
                               true, true, virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInSettingsFile() {
    loadSimpleApplication();
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInKotlinSettingsFile() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createNewLine(1)), false, virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInDeclarativeSettingsFile() {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_DECLARATIVE);
      runDeclarativeFakeModificationTest((factory, file) -> file.add(factory.createNewline()), false, virtualFile);
    });
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInKotlinBuildFile() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS);
    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runKtsFakeModificationTest((factory, file) -> file.add(factory.createNewLine(1)), false, virtualFile);
  }

  @Test
  public void testNotModifiedWhenAddingWhitespaceInDeclarativeBuildFile() {
    runWithDeclarativeSupport(() -> {
      loadSimpleDeclarativeApplication();
      VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder("app", FN_BUILD_GRADLE_DECLARATIVE);
      runGroovyFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
    });
  }

  @NotNull
  private VirtualFile getAppBuildFile() {
    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertThat(buildFile).isNotNull();
    return buildFile;
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManagerEx.getInstanceEx(projectRule.getProject()).findFile(file);
    assertThat(psiFile).isNotNull();
    return psiFile;
  }

  @NotNull
  private VirtualFile findOrCreateFileRelativeToProjectRootFolder(@NotNull String... names) {
    File filePath = findOrCreateFilePathRelativeToProjectRootFolder(names);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertThat(file).isNotNull();
    return file;
  }

  private @NotNull File findOrCreateFilePathRelativeToProjectRootFolder(@NotNull String... names) {
    File parent = getBaseDirPath(projectRule.getProject());
    for (int i = 0; i < names.length - 1; i++) {
      File child = new File(parent, names[i]);
      if (!child.exists()) {
        assertThat(child.mkdirs()).isTrue();
      }
      parent = child;
    }
    File result = new File(parent, names[names.length - 1]);
    assertThat(createIfNotExists(result)).isTrue();
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
    assertThat(DeclarativeStudioSupport.isEnabled()).isTrue();
    assertThat(DeclarativeIdeSupport.isEnabled()).isTrue();
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
    Project project = projectRule.getProject();
    PsiFile psiFile = findPsiFile(file);

    T factory = factoryFactory.apply(project);

    boolean filesModified = myGradleFiles.areGradleFilesModified();
    if (preCheckEnabled) {
      assertThat(filesModified).isFalse();
    }

    CommandProcessor.getInstance().executeCommand(project, () ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        editFunction.accept(factory, psiFile);
        psiFile.subtreeChanged();
      }), "Fake Edit Test", null);

    filesModified = myGradleFiles.areGradleFilesModified();
    if (expectedResult) {
      assertThat(filesModified).isTrue();
    }
    else {
      assertThat(filesModified).isFalse();
    }
  }
}