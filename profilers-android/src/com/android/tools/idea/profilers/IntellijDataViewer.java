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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.stacktrace.DataViewer;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;

public class IntellijDataViewer implements DataViewer {
  @NotNull
  private final JComponent myComponent;
  @Nullable
  private final Dimension myDimension;
  @NotNull
  private final Style myStyle;

  @NotNull
  public static IntellijDataViewer createImageViewer(@NotNull BufferedImage image) {
    return new IntellijDataViewer(new ResizableImage(image), new Dimension(image.getWidth(), image.getHeight()), Style.RAW);
  }

  @NotNull
  public static IntellijDataViewer createEditorViewer(@NotNull Disposable parent, @NotNull String content, @Nullable FileType contentType) {
    try {
      EditorFactory editorFactory = EditorFactory.getInstance();

      // We need to support documents with \r newlines in them (since network payloads can contain
      // data from any OS); however, Document will assert if it finds a \r as a line ending in its
      // content and the user will see a mysterious "NO PREVIEW" message without any information
      // on why. The Document class allows you to change a setting to allow \r, but this breaks
      // soft wrapping in the editor.
      content = content.replace("\r\n", "\n");

      Document document = editorFactory.createDocument(content.toCharArray());
      document.setReadOnly(true);

      EditorEx editor = (EditorEx)editorFactory.createViewer(document);
      editor.setCaretVisible(false);
      editor.getSettings().setLineNumbersShown(false);
      editor.getSettings().setLineMarkerAreaShown(false);
      editor.getSettings().setFoldingOutlineShown(false);
      editor.getSettings().setUseSoftWraps(true);

      if (contentType != null) {
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, contentType));
      }

      Disposer.register(parent, new Disposable() {
        @Override
        public void dispose() {
          editorFactory.releaseEditor(editor);
        }
      });
      return new IntellijDataViewer(editor.getComponent(), null, Style.RAW);
    }
    catch (Exception | AssertionError e) {
      // Exceptions and AssertionErrors can be thrown by editorFactory.createDocument and editorFactory.createViewer
      return createInvalidViewer();
    }
  }

  @NotNull
  public static IntellijDataViewer createPrettyViewer(@NotNull Project project, @NotNull byte[] content, @Nullable FileType contentType) {
    String fileExtension = contentType != null ? contentType.getDefaultExtension() : "";
    VirtualFile virtualFile = new LightVirtualFile(new Random().nextLong() + "." + fileExtension, contentType, new String(content));
    FileEditorProvider[] fileEditors = FileEditorProviderManager.getInstance().getProviders(project, virtualFile);
    FileEditor fileEditor = fileEditors.length != 0 ? fileEditors[0].createEditor(project, virtualFile) : null;
    if (!(fileEditor instanceof TextEditor)) {
      return createInvalidViewer();
    }
    EditorEx editorEx = (EditorEx) ((TextEditor)fileEditor).getEditor();
    editorEx.setViewer(true);
    editorEx.setCaretVisible(false);
    editorEx.getSettings().setLineNumbersShown(false);
    editorEx.getSettings().setLineMarkerAreaShown(false);
    editorEx.getFoldingModel().setFoldingEnabled(true);
    editorEx.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    Disposer.register(project, fileEditor);

    JComponent component = fileEditor.getComponent();
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(component, BorderLayout.CENTER);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile != null) {
      wrapper.setVisible(false);
      ReformatCodeProcessor processor = new ReformatCodeProcessor(psiFile, false);
      processor.setPostRunnable(() -> {
        try {
          virtualFile.setWritable(false);
        } catch (IOException ignored) {
        }
        // Fixes the viewer size to the post code reformat preferred size, that avoids the viewer changes its size on collapse/expand.
        wrapper.setPreferredSize(component.getPreferredSize());
        wrapper.setVisible(true);
      });
      processor.run();
    }
    return new IntellijDataViewer(wrapper, null, Style.PRETTY);
  }

  @NotNull
  public static IntellijDataViewer createInvalidViewer() {
    JComponent component = new JLabel("No preview available", SwingConstants.CENTER);
    component.setFont(component.getFont().deriveFont(14.f));
    return new IntellijDataViewer(component, null, Style.INVALID);
  }

  private IntellijDataViewer(@NotNull JComponent component, @Nullable Dimension dimension, @NotNull Style style) {
    myComponent = component;
    myDimension = dimension;
    myStyle = style;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public Dimension getDimension() {
    return myDimension;
  }

  @NotNull
  @Override
  public Style getStyle() {
    return myStyle;
  }
}
