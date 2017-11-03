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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class IntellijDataViewer implements DataViewer {
  @NotNull
  private final JComponent myComponent;
  @Nullable
  private final Dimension myDimension;

  @NotNull
  public static IntellijDataViewer createImageViewer(@NotNull BufferedImage image) {
    return new IntellijDataViewer(new ResizableImage(image), new Dimension(image.getWidth(), image.getHeight()));
  }

  @NotNull
  public static IntellijDataViewer createEditorViewer(@NotNull String content, @Nullable FileType contentType) {
    try {
      EditorFactory editorFactory = EditorFactory.getInstance();
      Document document = editorFactory.createDocument(content.toCharArray());
      document.setReadOnly(true);
      EditorEx editor = (EditorEx)editorFactory.createViewer(document);
      editor.setCaretVisible(false);
      editor.getSettings().setLineNumbersShown(false);
      editor.getSettings().setLineMarkerAreaShown(false);
      editor.getSettings().setFoldingOutlineShown(false);
      if (contentType != null) {
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, contentType));
      }
      return new IntellijDataViewer(editor.getComponent(), null);
    }
    catch (Exception | AssertionError e) {
      // Exceptions and AssertionErrors can be thrown by editorFactory.createDocument and editorFactory.createViewer
      return createInvalidViewer();
    }
  }

  @NotNull
  public static IntellijDataViewer createInvalidViewer() {
    JComponent component = new JLabel("No preview available", SwingConstants.CENTER);
    component.setFont(component.getFont().deriveFont(14.f));
    return new IntellijDataViewer(component, null);
  }

  private IntellijDataViewer(@NotNull JComponent component, @Nullable Dimension dimension) {
    myComponent = component;
    myDimension = dimension;
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
}
