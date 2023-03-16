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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * Tests for {@link GradleFiles}.
 */
public class GradleFilesTest extends AndroidGradleTestCase {
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
  protected File loadSimpleApplication() throws Exception {
    File projectRoot = super.loadSimpleApplication();
    // Make sure the file hashes are updated before the test is run
    myGradleFiles.maybeProcessSyncStarted();
    return projectRoot;
  }

  public void testNotModifiedWhenAddingWhitespaceInBuildFile() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false);
  }

  public void testNotModifiedWhenAddingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> {
      PsiFile dummyFile = factory.createGroovyFile("// foo", false, null);
      PsiElement comment = dummyFile.getFirstChild();
      file.add(comment);
    }, false);
  }

  public void testNotModifiedWhenEditingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + 3, "abc");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, false, buildFile);
  }

  public void testNotModifiedWhenAddingNewlineCommentToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n//");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, false, buildFile);
  }

  public void testNotModifiedWhenRemovingCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      file.deleteChildRange(file.getFirstChild(), file.getFirstChild());
    }, false, buildFile);
  }

  public void testModifiedWhenDeletingBringsProgramToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runFakeModificationTest((factory, file) -> {
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

    runFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.deleteString(1, 2);
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, true, buildFile);
  }

  public void testModifiedWhenAddingNewlineToCommentInBuildFile() throws Exception {
    loadSimpleApplication();
    VirtualFile buildFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);

    runFakeModificationTest((factory, file) -> {
      assertThat(file.getFirstChild() instanceof PsiComment).isTrue();
      Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
      doc.insertString(file.getFirstChild().getTextOffset() + "// Top-level".length(), "\n");
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
    }, true, buildFile);
  }

  public void testModifiedWhenAddingTextChildInBuildFile() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true);
  }

  public void testNotModifiedWhenAddingWhitespaceInSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                            virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInPropertiesFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!'")), true,
                            virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(project), project);
    VirtualFile virtualFile = wrapper.getPropertiesFile();
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInWrapperPropertiesFile() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(project), project);
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
      assertThat(file.getFirstChild() instanceof PsiComment).isFalse();
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
      file.addAfter(factory.createLineTerminator("\n   \t\t\n "), element);
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
    myGradleFiles.maybeProcessSyncStarted();
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
    myGradleFiles.maybeProcessSyncStarted();
    assertFalse(myGradleFiles.areGradleFilesModified());
  }

  public void testModifiedWhenModifiedDuringSync() throws Exception {
    loadSimpleApplication();
    myGradleFiles.maybeProcessSyncStarted();
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }, true);
    assertTrue(myGradleFiles.areGradleFilesModified());
  }

  public void testNotModifiedWhenChangedBackDuringSync() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.hello.application'"));
    }), true);
    myGradleFiles.maybeProcessSyncStarted();
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.bandroid.application'"));
    }, true);
    runFakeModificationTest(((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      file.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.hello.application'"));
    }), false, false, getAppBuildFile());
  }

  public void testModifiedWhenVersionCatalogFileChanged() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG);
    VirtualFile libs = findOrCreateFileRelativeToProjectRootFolder("gradle", "libs.versions.toml");
    runFakeModificationTest((factory, file) -> {
      assertThat(file.getChildren().length).isGreaterThan(0);
      PsiElement firstCopy = file.getChildren()[0].copy();
      file.getChildren()[0].add(firstCopy);
    }, true, libs);
  }

  public void testIsGradleFileWithBuildDotGradleFile() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithGradleDotPropertiesFile() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithWrapperPropertiesFile() throws IOException {
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(project), project);
    VirtualFile propertiesFile = wrapper.getPropertiesFile();
    PsiFile psiFile = findPsiFile(propertiesFile);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithKotlinSettings() {
    // We need to create a file with EventSystemEnabled == false to get the PsiFile to return a null virtual file.
    PsiFile psiFile = PsiFileFactory.getInstance(getProject())
      .createFileFromText(FN_SETTINGS_GRADLE_KTS, FileTypeManager.getInstance().getStdFileType("Kotlin"), "", 0L, false);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithRenamedKts() {
    PsiFile psiFile = PsiFileFactory.getInstance(getProject())
      .createFileFromText("app.gradle.kts", FileTypeManager.getInstance().getStdFileType("Kotlin"), "", 0L, false);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithVersionsToml() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder("gradle", "libs.versions.toml");
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testNothingInDefaultProject() {
    /* Prior to fix this would throw
    ERROR: Assertion failed: Please don't register startup activities for the default project: they won't ever be run
    java.lang.Throwable: Assertion failed: Please don't register startup activities for the default project: they won't ever be run
    at com.intellij.openapi.diagnostic.Logger.assertTrue(Logger.java:174)
    at com.intellij.ide.startup.impl.StartupManagerImpl.checkNonDefaultProject(StartupManagerImpl.java:80)
    at com.intellij.ide.startup.impl.StartupManagerImpl.registerPostStartupActivity(StartupManagerImpl.java:99)
    at com.android.tools.idea.gradle.project.sync.GradleFiles.<init>(GradleFiles.java:84)
     */

    // Default projects are initialized during the IDE build for example to generate the searchable index.
    GradleFiles gradleFiles = GradleFiles.getInstance(ProjectManager.getInstance().getDefaultProject());
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES); // not in the default project
    assertTrue(gradleFiles.isGradleFile(psiFile));
  }

  /**
   * Ensures hashes are not stored if the File does not exist.
   */
  public void testNoPhysicalFileExists() throws Exception {
    loadSimpleApplication();
    File path = VfsUtilCore.virtualToIoFile(getAppBuildFile());
    boolean deleted = path.delete();
    assertTrue(deleted);
    assertTrue(getAppBuildFile().exists());
    myGradleFiles.maybeProcessSyncStarted();
    // syncStarted adds a transaction to update the file hashes, ensure this is run before verifying
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(myGradleFiles.areGradleFilesModified());
    assertFalse(myGradleFiles.hasHashForFile(getAppBuildFile()));
  }

  public void testChangesAreNotDetectedWithNoListener() throws Exception {
    loadSimpleApplication();
    PsiFile psiFile = findPsiFile(getAppBuildFile());

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());

    // If the listener was attached, this should count as a modification.
    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ApplicationManager.getApplication().runWriteAction(() -> {
        assertThat(psiFile.getChildren().length).isGreaterThan(0);
        psiFile.getChildren()[0].replace(factory.createStatementFromText("apply plugin: 'com.hello.application'"));
      }), "Fake Edit Test", null);

    // But since we have no listener no files should be classed as modified.
    assertFalse(myGradleFiles.areGradleFilesModified());
  }

  public void testCommentingOutTriggersModification() throws Exception {
    loadSimpleApplication();
    runFakeModificationTest(((factory, file) -> {
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

  @NotNull
  private VirtualFile getAppBuildFile() {
    Module appModule = TestModuleUtil.findAppModule(getProject());
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);
    return buildFile;
  }

  @NotNull
  private PsiFile findOrCreatePsiFileRelativeToProjectRootFolder(@NotNull String... names) {
    VirtualFile file = findOrCreateFileRelativeToProjectRootFolder(names);
    return findPsiFile(file);
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

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult) {
    runFakeModificationTest(editFunction, expectedResult, true, getAppBuildFile());
  }

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult,
                                       @NotNull VirtualFile file) {
    runFakeModificationTest(editFunction, expectedResult, true, file);
  }

  private void runFakeModificationTest(@NotNull BiConsumer<GroovyPsiElementFactory, PsiFile> editFunction,
                                       boolean expectedResult,
                                       boolean preCheckEnabled,
                                       @NotNull VirtualFile file) {
    // Clear event queue as the hashing is added as a transaction
    UIUtil.dispatchAllInvocationEvents();

    PsiFile psiFile = findPsiFile(file);

    FileEditorManager mockManager = mock(FileEditorManager.class);

    myGradleFiles.getFileEditorListener().selectionChanged(new FileEditorManagerEvent(mockManager, null, null, file, null));

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

  public void testNotModifiedWhenAddingWhitespaceInKotlinSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_KTS);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInKotlinSettingsFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_SETTINGS_GRADLE_KTS);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!")), true,
                            virtualFile);
  }

  public void testNotModifiedWhenAddingWhitespaceInKotlinBuildFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runFakeModificationTest((factory, file) -> file.add(factory.createLineTerminator(1)), false, virtualFile);
  }

  public void testModifiedWhenAddingTextChildInKotlinBuildFile() throws Exception {
    loadSimpleApplication();

    VirtualFile virtualFile = findOrCreateFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_KTS);
    runFakeModificationTest((factory, file) -> file.add(factory.createExpressionFromText("ext.coolexpression = 'nice!")), true,
                            virtualFile);
  }
}