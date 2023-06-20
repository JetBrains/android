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

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceFilesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.Arrays;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class StringResourceEditorProvider implements FileEditorProvider, DumbAware {
  public static final String ID = "string-resource-editor";

  public static boolean canViewTranslations(@NotNull Project project, @NotNull VirtualFile file) {
    if (ResourceFilesUtil.getFolderType(file) != ResourceFolderType.VALUES) {
      return false;
    }

    Module m = ProjectFileIndex.getInstance(project).getModuleForFile(file);
    if (m == null || AndroidFacet.getInstance(m) == null) {
      return false;
    }

    String name = IdeResourcesUtil.getDefaultResourceFileName(ResourceType.STRING);
    assert name != null;

    // Shortcut for files names strings.xml
    if (file.getName().endsWith(name)) {
      return true;
    }

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return fileContainsStringResource(project, file);
    }
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> fileContainsStringResource(project, file));
  }


  public static boolean fileContainsStringResource(Project project, VirtualFile virtualFile) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile instanceof XmlFile) {
      // Check any values resources file with a <string> resource tag.
      XmlTag tag = ((XmlFile)psiFile).getRootTag();
      if (tag != null && tag.getName().equals(SdkConstants.TAG_RESOURCES)) {
        XmlTag[] subTags = tag.getSubTags();
        return Arrays.stream(subTags).anyMatch((it) -> it.getName().equals(SdkConstants.TAG_STRING));
      }
    }
    return false;
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
    VirtualFile stringsFile = StringsVirtualFile.getInstance(project, file);
    assert stringsFile != null;

    ApplicationManager.getApplication().invokeLater(() -> {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, stringsFile);
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
    return new StringResourceEditor((StringsVirtualFile)file);
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