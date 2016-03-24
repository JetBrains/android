/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import static com.android.SdkConstants.DOT_GRADLE;

public class GradleFileEditorProvider implements FileEditorProvider, DumbAware {

  /** FileEditorProvider ID for the layout editor */
  public static final String EDITOR_TYPE_ID = "android-gradle-editor";

  private static final boolean ENABLED = Boolean.getBoolean("new.android.gradle.editor.active");

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (!ENABLED) {
      return false;
    }
    return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE && file.getPath().endsWith(DOT_GRADLE);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new GradleFileEditor(file, project);
  }

  @Override
  public void disposeEditor(@NotNull FileEditor editor) {
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
