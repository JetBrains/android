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
package com.android.tools.idea.editors.strings;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeListener;

public class StringResourceEditor extends UserDataHolderBase implements FileEditor {
  public static final Icon ICON = AndroidIcons.Globe;
  public static final String NAME = "String Resource Editor";

  private StringResourceViewPanel myPanel;

  StringResourceEditor(@NotNull StringsVirtualFile file) {
    // Post startup activities (such as when reopening last open editors) are run from a background thread
    UIUtil.invokeAndWaitIfNeeded(() -> myPanel = new StringResourceViewPanel(file.getFacet(), this));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
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
    return true;
  }

  @Override
  public void selectNotify() {
    // TODO Doesn't refresh if a strings.xml file is deleted while the editor is visible
    if (!myPanel.dataIsCurrent()) {
      myPanel.reloadData();
    }
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

  @VisibleForTesting
  public JTable getTranslationsTable() {
    return myPanel.getTable();
  }

  public JTextComponent getKeyTextField() {
    return myPanel.myKeyTextField;
  }

  @VisibleForTesting
  public TextFieldWithBrowseButton getTranslationTextField() {
    return myPanel.myTranslationTextField;
  }
}
