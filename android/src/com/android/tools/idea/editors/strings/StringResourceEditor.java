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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceEditor extends UserDataHolderBase implements FileEditor {

  @NotNull private final StringsVirtualFile myStringsVirtualFile;
  public static final Icon ICON = StudioIcons.LayoutEditor.Toolbar.LANGUAGE;
  public static final String NAME = "String Resource Editor";

  private StringResourceViewPanel myPanel;

  StringResourceEditor(@NotNull StringsVirtualFile file) {
    myStringsVirtualFile = file;
    AndroidFacet facet = myStringsVirtualFile.getFacet();
    // Post startup activities (such as when reopening last open editors) are run from a background thread
    UIUtil.invokeAndWaitIfNeeded(() -> myPanel = new StringResourceViewPanel(facet, this));
  }

  @NotNull
  public static Font getFont(@NotNull Font defaultFont) {
    return JBFont.create(new Font(Font.DIALOG, Font.PLAIN, defaultFont.getSize()), !(defaultFont instanceof JBFont));
  }

  @NotNull
  public StringResourceViewPanel getPanel() {
    return myPanel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel.getLoadingPanel();
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
  @Nullable
  public StringsVirtualFile getFile() {
    return myStringsVirtualFile;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditor.super.getState(level);
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String toString() {
    return "StringResourceEditor " + myPanel.getFacet() + " " + System.identityHashCode(this);
  }
}
