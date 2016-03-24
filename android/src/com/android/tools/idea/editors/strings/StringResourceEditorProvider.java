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
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

public class StringResourceEditorProvider implements FileEditorProvider, DumbAware {
  public static final String ID = "string-resource-editor";

  public static boolean canViewTranslations(@NotNull Project project, @NotNull VirtualFile file) {
    if (!file.getName().equals(AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STRING))) {
      return false;
    }

    if (ResourceHelper.getFolderType(file) != ResourceFolderType.VALUES) {
      return false;
    }

    Module m = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(file);
    return m != null && AndroidFacet.getInstance(m) != null;
  }

  public static void openEditor(@NotNull final Module module) {
    final VirtualFile vf = StringsVirtualFile.getStringsVirtualFile(module);
    if (vf != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Project project = module.getProject();
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vf);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      });
    }
  }

  public static void openEditor(@NotNull final Project project, @NotNull VirtualFile file) {
    final VirtualFile vf = StringsVirtualFile.getInstance(project, file);
    ApplicationManager.getApplication().invokeLater(() -> {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vf);
      FileEditorManager.getInstance(project).openEditor(descriptor, true);
    });
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return file instanceof StringsVirtualFile;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new StringResourceEditor(project, file);
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