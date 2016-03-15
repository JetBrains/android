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
package com.android.tools.idea.editors.hierarchyview;

import com.android.tools.idea.editors.hierarchyview.model.ViewNode;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class HierarchyViewEditor extends UserDataHolderBase implements FileEditor {

  private final VirtualFile myVirtualFile;
  private final HierarchyViewCaptureOptions myOptions;

  private HierarchyViewer myViewer;

  public HierarchyViewEditor(@NotNull Project project, @NotNull VirtualFile file) throws IOException {
    myVirtualFile = file;
    myOptions = new HierarchyViewCaptureOptions();

    // Parse file
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(file.contentsToByteArray()));

    // Parse options
    myOptions.parse(input.readUTF());

    // Parse view node
    byte[] nodeBytes = new byte[input.readInt()];
    input.readFully(nodeBytes);
    ViewNode node = ViewNode.parseFlatString(nodeBytes);
    if (node == null) {
      throw new IOException("Error parsing view node");
    }

    byte[] previewBytes = new byte[input.readInt()];
    input.readFully(previewBytes);
    BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
    myViewer = new HierarchyViewer(node, preview);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myViewer.getRootComponent();
  }

  @Override
  public void dispose() {
    myViewer = null;
  }

  @NotNull
  @Override
  public String getName() {
    return null;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void deselectNotify() {
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void selectNotify() {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public boolean isValid() {
    return myVirtualFile.isValid();
  }
}
