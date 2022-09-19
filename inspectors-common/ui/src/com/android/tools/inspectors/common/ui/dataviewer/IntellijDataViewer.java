/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.dataviewer;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.ui.JBFont;
import java.util.Collections;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntellijDataViewer implements DataViewer {
  private static final int RAW_VIEWER_MAX_STRING_LENGTH = 500;

  @NotNull
  private final JComponent myComponent;
  @NotNull
  private final Style myStyle;

  /**
   * Create a data viewer that renders its content as is, without any attempt to clean it up.
   * <p>
   * Note: to prevent UI from being frozen by large text, the content will be truncated.
   */
  public static IntellijDataViewer createRawTextViewer(@NotNull byte[] content, Boolean isEditable) {
    JTextArea textArea = new JTextArea(new String(content, 0, Math.min(content.length, RAW_VIEWER_MAX_STRING_LENGTH)));
    textArea.setLineWrap(true);
    textArea.setEditable(isEditable);
    textArea.setBackground(null);
    textArea.setOpaque(false);
    return new IntellijDataViewer(textArea, Style.RAW);
  }

  /**
   * Create an editable data viewer that renders its content as is, without any attempt to clean it up.
   * <p>
   * Note: to prevent UI from being frozen by large text, the content will be truncated.
   */
  public static IntellijDataViewer createRawTextViewer(@NotNull byte[] content) {
    return createRawTextViewer(content, false);
  }

  /**
   * Create a data viewer that automatically formats the content it receives. In cases where it is
   * not able to do this, or it is not desirable to do this (e.g. plain text), it returns a
   * {@link Style#RAW} viewer instead. Be sure to check {@link IntellijDataViewer#getStyle()} if
   * this matters for your use-case.
   *
   * @param fileType An optional file type that can be associated with this content, which,
   *                 if provided, hints to the editor how it should format it.
   */
  @NotNull
  public static IntellijDataViewer createPrettyViewerIfPossible(@NotNull Project project,
                                                                byte[] content,
                                                                @Nullable FileType fileType) {
    try {
      EditorFactory editorFactory = EditorFactory.getInstance();

      // We need to support documents with \r newlines in them (since network payloads can contain
      // data from any OS); however, Document will assert if it finds a \r as a line ending in its
      // content and the user will see a mysterious "NO PREVIEW" message without any information
      // on why. The Document class allows you to change a setting to allow \r, but this breaks
      // soft wrapping in the editor.
      String contentStr = new String(content).replace("\r\n", "\n");

      Style style = Style.RAW;
      Document document = null;
      if (fileType instanceof LanguageFileType) {
        Language language = ((LanguageFileType)fileType).getLanguage();
        // Creating a "pretty" editor for plain text ends up feeling redundant, as none of the
        // features matter in that case. Just create a regular editor.
        if (!language.is(PlainTextLanguage.INSTANCE)) {
          PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(language, contentStr);
          if (psiFile != null) {
            ReformatCodeProcessor processor = new ReformatCodeProcessor(psiFile, false);
            processor.run();

            document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            if (document != null) {
              style = Style.PRETTY;
            }
          }
        }
      }

      if (document == null) {
        document = editorFactory.createDocument(contentStr.toCharArray());
      }

      EditorEx editor = (EditorEx)editorFactory.createViewer(document);
      editor.setCaretVisible(false);
      EditorSettings settings = editor.getSettings();

      settings.setLineNumbersShown(false);
      settings.setLineMarkerAreaShown(false);
      settings.setUseSoftWraps(true);

      settings.setSoftMargins(Collections.emptyList());
      settings.setRightMarginShown(false);

      settings.setFoldingOutlineShown(true);
      CodeFoldingManager.getInstance(project).updateFoldRegions(editor);

      if (fileType != null) {
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));
      }

      Disposer.register(project, () -> editorFactory.releaseEditor(editor));

      return new IntellijDataViewer(editor.getComponent(), style);
    }
    catch (Exception | AssertionError e) {
      // Exceptions and AssertionErrors can be thrown by editorFactory.createDocument and editorFactory.createViewer
      return createInvalidViewer();
    }
  }

  @NotNull
  public static IntellijDataViewer createInvalidViewer() {
    JComponent component = new JLabel("No preview available", SwingConstants.CENTER);
    component.setFont(JBFont.label().asPlain());
    return new IntellijDataViewer(component, Style.INVALID);
  }

  private IntellijDataViewer(@NotNull JComponent component, @NotNull Style style) {
    myComponent = component;
    myStyle = style;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  @Override
  public Style getStyle() {
    return myStyle;
  }
}
