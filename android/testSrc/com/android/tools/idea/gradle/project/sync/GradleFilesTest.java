/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleFiles}.
 */
public class GradleFilesTest extends AndroidGradleTestCase {
  @Mock private FileDocumentManager myDocumentManager;

  private GradleFiles myGradleFiles;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myGradleFiles = GradleFiles.getInstance(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myGradleFiles = null;
    super.tearDown();
  }

  @Override
  protected void loadSimpleApplication() throws Exception {
    super.loadSimpleApplication();
    // Make sure the file hashes are updated before the test is run
    myGradleFiles.getSyncListener().syncStarted(getProject(), false);
    myGradleFiles.getSyncListener().syncSucceeded(getProject());
  }

  public void testNotModifiedWhenAddingWhitespaceInBuildFile() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false);
  }

  public void testModifiedWhenAddingTextChildInBuildFile() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true);
  }

  public void testNotModifiedWhenAddingWhitespaceInSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileInProjectRootFolder(FN_SETTINGS_GRADLE);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileInProjectRootFolder(FN_SETTINGS_GRADLE);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                            virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileInProjectRootFolder(FN_GRADLE_PROPERTIES);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileInProjectRootFolder(FN_GRADLE_PROPERTIES);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                            virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                            virtualFile);
  }

  public void testModifiedWhenReplacingChild() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }), true);
  }

  public void testModifiedWhenChildRemoved() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }), true);
  }

  public void testNotModifiedWhenInnerWhiteSpaceIsAdded() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      element.addAfter(factory.createWhiteSpace(), element.getLastChild());
    }), false);
  }

  public void testNotModifiedWhenInnerNewLineIsAdded() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isAtLeast(3);
      PsiElement element = file.getChildren()[2];
      assertThat(element).isInstanceOf(GrMethodCallExpression.class);
      element.addAfter(factory.createLineTerminator("\n   \t\t\n "), element.getLastChild());
    }), false);
  }

  public void testNotModifiedWhenTextIsIdentical() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }), true);
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.android.application'"));
    }), false, false, getAppBuildFile());
  }

  public void testModifiedWhenDeleteAfterSync() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }), true);
    myGradleFiles.getSyncListener().syncStarted(getProject(), false);
    myGradleFiles.getSyncListener().syncSucceeded(getProject());
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.android.application'"));
    }), true);
  }

  public void testNotModifiedAfterSync() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }, true);
    myGradleFiles.getSyncListener().syncStarted(getProject(), false);
    myGradleFiles.getSyncListener().syncSucceeded(getProject());
    assertFalse(myGradleFiles.areGradleFilesModified());
  }

  public void testModifiedWhenModifiedDuringSync() throws Exception {
    loadSimpleApplication();
    myGradleFiles.getSyncListener().syncStarted(getProject(), false);
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }, true);
    myGradleFiles.getSyncListener().syncSucceeded(getProject());
    assertTrue(myGradleFiles.areGradleFilesModified());
  }

  public void testNotModifiedWhenChangedBackDuringSync() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.hello.application'"));
    }), true);
    myGradleFiles.getSyncListener().syncStarted(getProject(), false);
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }, true);
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.hello.application'"));
    }), false, false, getAppBuildFile());
    myGradleFiles.getSyncListener().syncSucceeded(getProject());
  }

  public void testIsGradleFileWithBuildDotGradleFile() {
    PsiFile psiFile = findOrCreatePsiFileInProjectRootFolder(FN_BUILD_GRADLE);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithGradleDotPropertiesFile() {
    PsiFile psiFile = findOrCreatePsiFileInProjectRootFolder(FN_GRADLE_PROPERTIES);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithWrapperPropertiesFile() throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    VirtualFile propertiesFile = wrapper.getPropertiesFile();
    PsiFile psiFile = findPsiFile(propertiesFile);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  @NotNull
  private VirtualFile getAppBuildFile() {
    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);
    return buildFile;
  }

  @NotNull
  private PsiFile findOrCreatePsiFileInProjectRootFolder(@NotNull String fileName) {
    VirtualFile file = findOrCreateFileInProjectRootFolder(fileName);
    return findPsiFile(file);
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).findFile(file);
    assertNotNull(psiFile);
    return psiFile;
  }

  @NotNull
  private VirtualFile findOrCreateFileInProjectRootFolder(@NotNull String fileName) {
    File filePath = findOrCreateFilePathInProjectRootFolder(fileName);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull(file);
    return file;
  }

  @NotNull
  private File findOrCreateFilePathInProjectRootFolder(@NotNull String fileName) {
    File filePath = new File(getBaseDirPath(getProject()), fileName);
    assertTrue(createIfNotExists(filePath));
    return filePath;
  }

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult) throws Exception {
    runFakeModificationTest(editFunction, expectedResult, true, getAppBuildFile());
  }

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult,
                                       @NotNull VirtualFile file) throws Exception {
    runFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void simulateUnsavedChanges(@NotNull VirtualFile file) {
    when(myDocumentManager.isFileModified(file)).thenReturn(true);
  }

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult,
                                       boolean preCheckEnabled,
                                       @NotNull VirtualFile file) throws Exception {
    PsiFile psiFile = findPsiFile(file);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());

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