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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static com.intellij.util.ArrayUtilRt.EMPTY_INT_ARRAY;

/**
 * Integration tests for 'Gradle Sync' and the Gradle build cache.
 */
public class BuildCacheSyncTest extends AndroidGradleTestCase {
  // See https://code.google.com/p/android/issues/detail?id=229633
  public void testSyncWithGradleBuildCacheUninitialized() throws Exception {
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);
    setBuildCachePath(createTempDirectory("build-cache", ""));

    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);

    VirtualFile rootDirectory = project.getBaseDir();
    VirtualFile mainActivityFile =
      rootDirectory.findFileByRelativePath("app/src/main/java/com/example/alruiz/transitive_dependencies/MainActivity.java");

    MatchByDescription matchByDescription = new MatchByDescription("Cannot resolve symbol 'AppCompatActivity'");
    List<HighlightInfo> highlights = getErrorHighlights(mainActivityFile, matchByDescription);
    // It is expected that AppCompatActivity cannot be resolved yet, since AARs have not been exploded yet.
    assertThat(highlights).isNotEmpty();

    // Generate sources to explode AARs in build cache
    GradleInvocationResult result = generateSources();
    assertTrue(result.isBuildSuccessful());

    highlights = getErrorHighlights(mainActivityFile, matchByDescription);
    // AppCompatActivity should be resolved now.
    assertThat(highlights).isEmpty();
  }

  private void setBuildCachePath(@NotNull File path) throws IOException {
    // Set up path of build-cache
    // See: http://tools.android.com/tech-docs/build-cache
    Project project = getProject();
    File gradlePropertiesFilePath = new File(getBaseDirPath(project), "gradle.properties");
    Properties gradleProperties = getProperties(gradlePropertiesFilePath);
    gradleProperties.setProperty("android.enableBuildCache", "true");
    gradleProperties.setProperty("android.buildCacheDir", path.getAbsolutePath());
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, "");
  }

  @NotNull
  private List<HighlightInfo> getErrorHighlights(@NotNull VirtualFile file, @NotNull Predicate<HighlightInfo> filter) throws InterruptedException {
    Editor editor = openTextEditor(file);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    Project project = getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    List<HighlightInfo> highlightInfos = CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, EMPTY_INT_ARRAY, true);
    return highlightInfos.stream().filter(filter).collect(Collectors.toList());
  }

  @NotNull
  private Editor openTextEditor(@NotNull VirtualFile file) {
    Project project = getProject();
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 0), false);
    ((EditorImpl)editor).setCaretActive();
    return editor;
  }

  private static class MatchByDescription implements Predicate<HighlightInfo> {
    @NotNull private final String myExpectedDescription;

    MatchByDescription(@NotNull String expectedDescription) {
      myExpectedDescription = expectedDescription;
    }

    @Override
    public boolean test(HighlightInfo info) {
      return info != null && myExpectedDescription.equals(info.getDescription());
    }
  }
}
