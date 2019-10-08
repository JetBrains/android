/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link FileEditor} used for providing undo inside the {@link MotionAccessoryPanel}.
 *
 * The Undo/Redo actions uses a data context to access a {@link FileEditor}.
 * This {@link FileEditor} is not used for opening the motion editor.
 */
public class MotionFileEditor implements FileEditor, DocumentReferenceProvider {
  private final VirtualFile myMotionSceneFile;
  private final JComponent myComponent;
  private final Collection<DocumentReference> myDocumentReferences;

  public MotionFileEditor(@NotNull JComponent component, @NotNull VirtualFile motionSceneFile) {
    myMotionSceneFile = motionSceneFile;
    myComponent = component;
    myDocumentReferences = Collections.singletonList(DocumentReferenceManager.getInstance().create(motionSceneFile));
    DataManager.registerDataProvider(component, dataId -> {
      if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
        return this;
      }
      return null;
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "MotionEditor";
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
  public VirtualFile getFile() {
    return myMotionSceneFile;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }

  @Override
  @NotNull
  public Collection<DocumentReference> getDocumentReferences() {
    return myDocumentReferences;
  }
}
