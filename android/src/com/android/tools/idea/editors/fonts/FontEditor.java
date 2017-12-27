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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * {@link FileEditor} that providers preview for TTF fonts.
 */
class FontEditor implements FileEditor {
  private static final Logger LOG = Logger.getInstance(FontEditor.class);

  private static final String NAME = "Font";

  private static final float MAX_FONT_SIZE = UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + JBUI.scale(30f);
  private static final float MIN_FONT_SIZE = UIUtil.getFontSize(UIUtil.FontSize.MINI);
  private static final Border BORDER = JBUI.Borders.empty(50);

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final JTextArea myTextArea;
  private final VirtualFile myFile;

  private final JPanel myRootPanel;
  private float myCurrentFontSize;

  public FontEditor(@NotNull VirtualFile file) {
    myFile = file;
    myRootPanel = new JPanel(new BorderLayout());
    myTextArea = new JTextArea(BorderLayout.CENTER);

    myTextArea.setLineWrap(true);
    myTextArea.setWrapStyleWord(true);
    myTextArea.setBorder(BORDER);

    myCurrentFontSize = UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + JBUI.scale(15f);

    try {
      // Derive the font and set it to large
      Font font = Font.createFont(Font.TRUETYPE_FONT, file.getInputStream()).deriveFont(myCurrentFontSize);

      myTextArea.setFont(font);
      myTextArea.setText(font.getFontName() + "\n\n" + new LoremGenerator().generate(50, true));
      myTextArea.addMouseWheelListener(e -> {
        float increment = (e.getWheelRotation() < 0) ? -1f : 1f;

        float newFontSize = Math.min(Math.max(MIN_FONT_SIZE, myCurrentFontSize + increment), MAX_FONT_SIZE);
        if (newFontSize != myCurrentFontSize) {
          myCurrentFontSize = newFontSize;
          Font newFont = myTextArea.getFont().deriveFont(myCurrentFontSize);
          myTextArea.setFont(newFont);
        }
      });
    }
    catch (FontFormatException | IOException e) {
      String message = "Unable to open font " + file.getName();

      myTextArea.setFont(UIUtil.getLabelFont());
      myTextArea.setEditable(false);
      myTextArea.setText(message);
      LOG.warn(message ,e);
    }
    myRootPanel.add(myTextArea);
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
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
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
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
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
