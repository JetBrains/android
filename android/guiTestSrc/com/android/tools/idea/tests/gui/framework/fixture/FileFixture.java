/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.CommonProcessors;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;

public class FileFixture {
  @NotNull private final Project myProject;
  @NotNull private final File myPath;
  @NotNull private final VirtualFile myVirtualFile;

  public FileFixture(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myPath = virtualToIoFile(file);
    myVirtualFile = file;
  }

  @NotNull
  public FileFixture requireOpenAndSelected() {
    requireVirtualFile();
    pause(new Condition("File " + quote(myPath.getPath()) + " to be opened") {
      @Override
      public boolean test() {
        return GuiActionRunner.execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return isOpenAndSelected();
          }
        });
      }
    }, SHORT_TIMEOUT);
    return this;
  }

  private boolean isOpenAndSelected() {
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    FileEditor selectedEditor = editorManager.getSelectedEditor(myVirtualFile);
    if (selectedEditor != null) {
      JComponent component = selectedEditor.getComponent();
      if (component.isVisible() && component.isShowing()) {
        Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
        if (document != null) {
          PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
          if (psiFile != null) {
            DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
            boolean isRunning = method("isRunning").withReturnType(boolean.class).in(codeAnalyzer).invoke();
            return !isRunning;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  public FileFixture requireCodeAnalysisHighlightCount(@NotNull final HighlightSeverity severity, int expected) {
    final Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    assertNotNull("No Document found for path " + quote(myPath.getPath()), document);

    Collection<HighlightInfo> highlightInfos = GuiActionRunner.execute(new GuiQuery<Collection<HighlightInfo>>() {
      @Override
      protected Collection<HighlightInfo> executeInEDT() throws Throwable {
        CommonProcessors.CollectProcessor<HighlightInfo> processor = new CommonProcessors.CollectProcessor<HighlightInfo>();
        DaemonCodeAnalyzerEx.processHighlights(document, myProject, severity, 0, document.getTextLength(), processor);
        return processor.getResults();
      }
    });

    assertThat(highlightInfos).hasSize(expected);
    return this;
  }

  @NotNull
  public FileFixture waitForCodeAnalysisHighlightCount(@NotNull final HighlightSeverity severity, final int expected) {
    final Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    assertNotNull("No Document found for path " + quote(myPath.getPath()), document);

    Pause.pause(new Condition("Waiting for code analysis " + severity + " count to reach " + expected) {
      @Override
      public boolean test() {
        Collection<HighlightInfo> highlightInfos = GuiActionRunner.execute(new GuiQuery<Collection<HighlightInfo>>() {
          @Override
          protected Collection<HighlightInfo> executeInEDT() throws Throwable {
            CommonProcessors.CollectProcessor<HighlightInfo> processor = new CommonProcessors.CollectProcessor<HighlightInfo>();
            DaemonCodeAnalyzerEx.processHighlights(document, myProject, severity, 0, document.getTextLength(), processor);
            return processor.getResults();
          }
        });
        return highlightInfos.size() == expected;
      }
    });

    return this;
  }

  @NotNull
  public FileFixture requireVirtualFile() {
    assertNotNull("No VirtualFile found for path " + quote(myPath.getPath()), myVirtualFile);
    return this;
  }
}
