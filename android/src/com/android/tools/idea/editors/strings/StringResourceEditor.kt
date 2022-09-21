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

import com.android.tools.idea.res.ResourceNotificationManager;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final ResourceNotificationManager.ResourceChangeListener myResourceChangeListener;
  // We sometimes get extra calls to `selectNotify`. This ensures that we know when
  // those calls represent a real transition.
  private final AtomicBoolean mySelected = new AtomicBoolean();
  private @Nullable ResourceNotificationManager.ResourceVersion myResourceVersion = null;

  StringResourceEditor(@NotNull StringsVirtualFile file) {
    myStringsVirtualFile = file;
    AndroidFacet facet = myStringsVirtualFile.getFacet();
    // Post startup activities (such as when reopening last open editors) are run from a background thread
    UIUtil.invokeAndWaitIfNeeded(() -> myPanel = new StringResourceViewPanel(facet, this));
    myResourceChangeListener = reason -> {
      if (reason.contains(ResourceNotificationManager.Reason.RESOURCE_EDIT)) {
        myPanel.reloadData();
      }
    };
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
  public StringsVirtualFile getFile() {
    return myStringsVirtualFile;
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
    return true;
  }

  @Override
  public void selectNotify() {
    if (mySelected.compareAndSet(false, true)) {
      addListener();
    }
  }

  @Override
  public void deselectNotify() {
    if (mySelected.compareAndSet(true, false)) {
      removeListener();
    }
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

  @NotNull
  @Override
  public String toString() {
    return "StringResourceEditor " + myPanel.getFacet() + " " + System.identityHashCode(this);
  }

  private void addListener() {
    AndroidFacet facet = myStringsVirtualFile.getFacet();
    Project project = facet.getModule().getProject();
    ResourceNotificationManager.ResourceVersion latest = ResourceNotificationManager.getInstance(project)
      .addListener(myResourceChangeListener, facet, /* file= */ null, /* configuration= */ null);
    if (myResourceVersion != null && myResourceVersion != latest) {
      myPanel.reloadData();
    }
  }

  private void removeListener() {
    AndroidFacet facet = myStringsVirtualFile.getFacet();
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(facet.getModule().getProject());
    myResourceVersion = manager.getCurrentVersion(facet, /* file= */ null, /* configuration= */null);
    manager.removeListener(myResourceChangeListener, facet, /* file= */ null, /* configuration= */ null);
  }
}
