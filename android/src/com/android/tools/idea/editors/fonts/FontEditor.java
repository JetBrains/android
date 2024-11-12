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

package com.android.tools.idea.editors.fonts;

import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * {@link FileEditor} that providers preview for TTF fonts.
 */
class FontEditor implements FileEditor {
  private static final Logger LOG = Logger.getInstance(FontEditor.class);

  private static final String NAME = "Font";
  private static final String LOREM_TEXT = new LoremGenerator().generate(50, true);

  private static final float MAX_FONT_SIZE = UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + JBUI.scale(30f);
  private static final float MIN_FONT_SIZE = UIUtil.getFontSize(UIUtil.FontSize.MINI);
  private static final Border BORDER = JBUI.Borders.empty(10);
  private static final Font DEFAULT_FONT = StartupUiUtil.getLabelFont();

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final JTextArea myTextArea;
  private final VirtualFile myFile;

  private final JPanel myRootPanel;
  private float myCurrentFontSize;
  private final JLabel myFontNameLabel;

  @NotNull
  private static JTextArea createTextArea() {
    JTextArea area = new JTextArea();
    area.setLineWrap(true);
    area.setWrapStyleWord(true);

    return area;
  }

  /**
   * Find the text that we can display using the given font. If the font can render lorem ipsum, that will be used. Otherwise
   * we find visible glyphs that we can render.
   * If there is nothing that can be displayed using this font, an empty string is returned.
   */
  @NotNull
  private static String findDisplayableText(@NotNull Font font) {
    if (font.canDisplayUpTo(LOREM_TEXT) == -1) {
      // Everything can be displayed
      return LOREM_TEXT;
    }

    StringBuilder displayableString = new StringBuilder(50);
    // Display a maximum of 250 glyphs
    int numGlyphs = Math.min(font.getNumGlyphs(), 250);
    int displayedGlyphs = 0;
    for (int i = Character.MIN_CODE_POINT; i < Character.MAX_CODE_POINT; i++) {
      if (!Character.isValidCodePoint(i)) {
        continue;
      }
      if (displayedGlyphs >= numGlyphs) {
        return displayableString.toString();
      }
      if (font.canDisplay(i)) {
        displayedGlyphs++;
        displayableString.appendCodePoint(i);
      }
    }

    return "";
  }

  public FontEditor(@NotNull VirtualFile file) {
    myFile = file;
    myRootPanel = new JPanel(new BorderLayout());
    myRootPanel.setBackground(UIUtil.getTextFieldBackground());
    myRootPanel.setBorder(BORDER);
    myFontNameLabel = new JLabel();
    myTextArea = createTextArea();

    myCurrentFontSize = UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + JBUI.scale(10f);

    try {
      // Derive the font and set it to large
      Font font = Font.createFont(Font.TRUETYPE_FONT, file.getInputStream()).deriveFont(myCurrentFontSize);

      myFontNameLabel.setText(font.getFontName());
      if (font.canDisplayUpTo(font.getFontName()) == -1) {
        myFontNameLabel.setFont(font);
      }
      else {
        myFontNameLabel.setFont(DEFAULT_FONT.deriveFont(myCurrentFontSize));
      }
      myFontNameLabel.setBorder(JBUI.Borders.emptyBottom(5));

      String displayableText = findDisplayableText(font);
      if (!displayableText.isEmpty()) {
        myTextArea.setFont(font);
        myTextArea.setText(displayableText);
      }
      else {
        // We can not display anything using these font so just show a message
        myTextArea.setFont(DEFAULT_FONT);
        myTextArea.setEditable(false);
        myTextArea.setText("This font does not contain any glyphs that can be previewed");
      }
      myRootPanel.addMouseWheelListener(this::onMouseWheelEvent);
    }
    catch (FontFormatException | IOException e) {
      String message = "Unable to open font " + file.getName();

      myTextArea.setFont(StartupUiUtil.getLabelFont());
      myTextArea.setEditable(false);
      myTextArea.setText(message);
      LOG.warn(message ,e);
    }
    myRootPanel.add(myFontNameLabel, BorderLayout.NORTH);
    // The JTextArea needs to be put inside a scroll pane,
    // otherwise its width will expand to occupy the entire IDE
    JBScrollPane scrollPane = new JBScrollPane();
    scrollPane.getViewport().add(myTextArea);
    myRootPanel.add(scrollPane, BorderLayout.CENTER);
  }

  private void onMouseWheelEvent(MouseWheelEvent e) {
    // On some systems (e.g. macOS), scrolling can result in a ton of getWheelRotation == 0 events.
    if (e.getWheelRotation() == 0) {
      return;
    }

    float increment = (e.getWheelRotation() < 0) ? -1f : 1f;

    float newFontSize = Math.min(Math.max(MIN_FONT_SIZE, myCurrentFontSize + increment), MAX_FONT_SIZE);
    if (newFontSize != myCurrentFontSize) {
      myCurrentFontSize = newFontSize;
      myTextArea.setFont(myTextArea.getFont().deriveFont(myCurrentFontSize));
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextArea;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }


  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
