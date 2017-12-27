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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.idea.smali.SmaliFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Viewer for dex byte code. Based on IntelliJ's ByteCodeViewerComponent.
 */
public class DexCodeViewer extends JPanel implements Disposable {
  private final Editor myEditor;

  public DexCodeViewer(@NotNull Project project, @NotNull String byteCode) {
    super(new BorderLayout());

    final EditorFactory factory = EditorFactory.getInstance();
    final Document doc = ((EditorFactoryImpl)factory).createDocument(byteCode, true, false);
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);

    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(SmaliFileType.getInstance(), project, null);
    ((EditorEx)myEditor).setHighlighter(editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme()));
    ((EditorEx)myEditor).setCaretVisible(true);

    final EditorSettings settings = myEditor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);

    myEditor.setBorder(null);
    add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}