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

import com.android.tools.profilers.ContentType;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.stacktrace.DataViewer;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.ex.EditorEx;
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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Create a data viewer that renders its content as is, without any attempt to clean it up.
   */
  public static IntellijDataViewer createRawTextViewer(@NotNull byte[] content) {
    JTextArea textArea = new JTextArea(new String(content));
    textArea.setLineWrap(true);
    textArea.setFont(ProfilerFonts.H4_FONT);
    textArea.setEditable(false);
    textArea.setBackground(null);
    return new IntellijDataViewer(textArea, null, Style.RAW);
  }


  /**
   * Create a data viewer that automatically formats the content it receives.
   *
   * @param fileType An optional file type that can be associated with this content, which,
   *                 if provided, hints to the editor how it should format it.
   */
  @NotNull
  public static IntellijDataViewer createPrettyViewer(@NotNull Project project,
                                                      @NotNull byte[] content,
                                                      @Nullable FileType fileType) {
    String fileExtension = fileType != null ? fileType.getDefaultExtension() : "";
    VirtualFile virtualFile = new LightVirtualFile(new Random().nextLong() + "." + fileExtension, fileType, new String(content));
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
