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

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LocalResourceRepositoryAsVirtualFile;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

public class StringResourceEditorProvider implements FileEditorProvider, DumbAware {
  public static final String ID = "string-resource-editor";

  public static boolean canViewTranslations(@NotNull VirtualFile file) {
    if (Boolean.getBoolean("STRINGS_EDITOR")) {
      return file.getName().equals(AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING)) &&
             ResourceHelper.getFolderType(file) == ResourceFolderType.VALUES;
    }
    return false;
  }

  public static void openEditor(@NotNull final Project project, @NotNull VirtualFile file) {
    final LocalResourceRepositoryAsVirtualFile vf = LocalResourceRepositoryAsVirtualFile.getInstance(project, file);
    if (vf == null) {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(project, "Cannot read project resources", StringResourceEditor.NAME);
        }
      }, ModalityState.defaultModalityState());
      return;
    }
    vf.setIcon(StringResourceEditor.ICON);
    vf.setProject(project);
    vf.setName(StringResourceEditor.NAME);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vf);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      }
    });
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file instanceof LocalResourceRepositoryAsVirtualFile;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new StringResourceEditor(project, file);
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
    return ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }
}