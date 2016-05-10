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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.tools.idea.apk.viewer.arsc.ArscViewer;
import com.android.tools.idea.apk.viewer.dex.DexFileViewer;
import com.android.tools.idea.editors.NinePatchEditorProvider;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class ApkEditor implements FileEditor, ApkViewPanel.Listener {
  private final Project myProject;
  private final VirtualFile myBaseFile;
  private final VirtualFile myRoot;
  private final ApkViewPanel myApkViewPanel;

  private JBSplitter mySplitter;
  private ApkFileEditorComponent myCurrentEditor;

  public ApkEditor(@NotNull Project project, @NotNull VirtualFile baseFile, @NotNull VirtualFile root) {
    myProject = project;
    myBaseFile = baseFile;
    myRoot = root;

    mySplitter = new JBSplitter(true, "android.apk.viewer", 0.62f);

    myApkViewPanel = new ApkViewPanel(new ApkParser(baseFile, root));
    myApkViewPanel.setListener(this);

    mySplitter.setFirstComponent(myApkViewPanel.getContainer());
    mySplitter.setSecondComponent(new JPanel());
  }

  /**
   * Changes the editor displayed based on the path selected in the tree.
   */
  @Override
  public void selectionChanged(@Nullable ApkEntry entry) {
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
    }

    VirtualFile file = entry == null ? null : entry.file;

    myCurrentEditor = getEditor(file);
    mySplitter.setSecondComponent(myCurrentEditor.getComponent());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myApkViewPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return myBaseFile.getName();
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
    return myBaseFile.isValid();
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
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
      myCurrentEditor = null;
    }
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }

  @NotNull
  private ApkFileEditorComponent getEditor(@Nullable VirtualFile file) {
    if (file == null) {
      return new EmptyPanel();
    }

    if (ApkFileSystem.getInstance().isArsc(file)) {
      byte[] arscContent;
      try {
        arscContent = file.contentsToByteArray();
      }
      catch (IOException e) {
        return new EmptyPanel();
      }
      return new ArscViewer(arscContent);
    }

    if (SdkConstants.EXT_DEX.equals(file.getExtension())) {
      return new DexFileViewer(file);
    }

    Optional<FileEditorProvider> providers = getFileEditorProviders(file);
    if (!providers.isPresent()) {
      return new EmptyPanel();
    }
    else {
      FileEditor editor = providers.get().createEditor(myProject, file);
      return new ApkFileEditorComponent() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return editor.getComponent();
        }

        @Override
        public void dispose() {
          Disposer.dispose(editor);
        }
      };
    }
  }

  @NotNull
  private Optional<FileEditorProvider> getFileEditorProviders(@Nullable VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return Optional.empty();
    }

    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(myProject, file);

    // skip 9 patch editor since nine patch information has been stripped out
    return Arrays.stream(providers).filter(
      fileEditorProvider -> !(fileEditorProvider instanceof NinePatchEditorProvider)).findFirst();
  }
}
