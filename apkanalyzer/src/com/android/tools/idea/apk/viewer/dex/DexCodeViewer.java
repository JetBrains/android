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

import com.intellij.CommonBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import org.jetbrains.annotations.Nullable;

/**
 * Viewer for dex byte code. Based on IntelliJ's ByteCodeViewerComponent.
 */
public class DexCodeViewer extends DialogWrapper {
  private final Editor myEditor;

  public DexCodeViewer(@NotNull Project project, @NotNull String nodeName, @NotNull String byteCode) {
    super(project, false);
    setModal(false);
    setCancelButtonText(CommonBundle.message("button.without.mnemonic.close"));
    setOKButtonText("Set");
    getOKAction().setEnabled(false);
    setCrossClosesWindow(true);
    setResizable(true);
    setTitle("DEX Byte Code for " + nodeName);

    final EditorFactory factory = EditorFactory.getInstance();
    final Document doc = ((EditorFactoryImpl)factory).createDocument(byteCode, true, false);
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);

    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    //SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(getSmaliFileTypeOrFallback(), project, null);
    //((EditorEx)myEditor).setHighlighter(editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme()));
    ((EditorEx)myEditor).setCaretVisible(true);

    final EditorSettings settings = myEditor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);

    myEditor.setBorder(null);

    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(myEditor.getComponent());
    panel.setPreferredSize(JBUI.size(640, 480));
    return panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myEditor.getComponent();
  }

  @Override
  protected @NonNls @Nullable String getDimensionServiceKey() {
    return ShowDisassemblyAction.class.getName();
  }

  private static @NotNull FileType getSmaliFileTypeOrFallback() {
    FileType smaliFileType = FileTypeManager.getInstance().findFileTypeByName("Smali");
    if (smaliFileType == null) return FileTypes.UNKNOWN;

    return smaliFileType;
  }

  @Override
  public void dispose() {
    super.dispose();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}