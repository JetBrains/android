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

import com.android.SdkConstants;
import com.android.utils.SdkUtils;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LayeredImageEditorProvider implements FileEditorProvider, DumbAware {
  @NonNls private static final String EDITOR_TYPE_ID = "layeredimageeditor";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return SdkUtils.endsWithIgnoreCase(file.getPath(), SdkConstants.DOT_PSD);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    // Use the default image editor to reuse the standard zoom UI, etc.
    FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider("images");
    // There is always a standard images provider
    assert provider != null;

    FileEditor editor = provider.createEditor(project, file);
    return new LayeredImageEditor(project, file, editor);
  }

  @Override
  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return EDITOR_TYPE_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
