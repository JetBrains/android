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
package com.android.tools.idea.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;
import static com.intellij.util.ArrayUtilRt.EMPTY_INT_ARRAY;
import static org.junit.Assert.assertNotNull;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Utility functions for working with HighlightInfos in a source file
 */
public final class HighlightInfos {

  /**
   * Return a List of all the HighlightInfos in a file filtered by the given predicate (the predicate does not need to check for null as
   * this method does that).
   */
  @NotNull
  public static List<HighlightInfo> getHighlightInfos(@NotNull Project project, @NotNull File pathInProject, @NotNull Predicate<HighlightInfo> filter)
    throws InterruptedException {

    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(project.getBasePath(), pathInProject.getPath()));
    assertNotNull(file);

    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 0), false);
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();

    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assertNotNull(psiFile);
    DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    List<HighlightInfo> highlightInfos = CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, EMPTY_INT_ARRAY, true);
    return highlightInfos.stream().filter(info -> info != null).filter(filter).collect(Collectors.toList());
  }

  /**
   * Checks that a file has no highlights with a severity higher than a warning.
   */
  public static void assertFileHasNoErrors(@NotNull Project project, @NotNull File pathInProject) throws InterruptedException {
    assertThat(getHighlightInfos(project, pathInProject, highlight -> highlight.getSeverity().compareTo(WARNING) > 0)).isEmpty();
  }
}
