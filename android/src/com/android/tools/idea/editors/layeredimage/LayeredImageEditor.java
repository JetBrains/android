/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.editors.layeredimage;

import com.android.tools.pixelprobe.Image;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;

class LayeredImageEditor extends UserDataHolderBase implements FileEditor {
  private final FileEditor myEditor;
  private final DumbService myDumbService;
  private final LayersManager myLayersManager;
  private final ImageEditor myImageEditor;

  private Image myImage;
  private LayeredImageEditorPanel myEditorPanel;

  LayeredImageEditor(Project project, VirtualFile file, FileEditor editor) {
    myEditor = editor;
    myDumbService = DumbService.getInstance(project);
    myLayersManager = LayersManager.get(project);

    myImageEditor = ((ImageFileEditor) editor).getImageEditor();
    try {
      myImage = Utilities.loadImage(file);
      myImageEditor.getDocument().setValue(Utilities.getDisplayableImage(myImage));
    }
    catch (IOException e) {
      Logger.getInstance(LayeredImageEditorProvider.class).error(
        "Unexpected exception while reading Photoshop file " + file.getCanonicalPath(), e);
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEditor);
    getComponent().dispose();
  }

  @NotNull
  @Override
  public LayeredImageEditorPanel getComponent() {
    if (myEditorPanel == null) {
      myEditorPanel = new LayeredImageEditorPanel(myImageEditor, myImage);
      myDumbService.smartInvokeLater(() -> myLayersManager.bind(myEditorPanel));
    }
    return myEditorPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getComponent().getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "LayeredImageEditor";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myEditor.getState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    myEditor.setState(state);
  }

  @Override
  public boolean isModified() {
    return myEditor.isModified();
  }

  @Override
  public boolean isValid() {
    return myEditor.isValid();
  }

  @Override
  public void selectNotify() {
    myEditor.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myEditor.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myEditor.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myEditor.removePropertyChangeListener(listener);
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myEditor.getBackgroundHighlighter();
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return myEditor.getCurrentLocation();
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return myEditor.getStructureViewBuilder();
  }
}
