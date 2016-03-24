/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.RenderService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorProvider implements FileEditorProvider, DumbAware {
  /** FileEditorProvider ID for the layout editor */
  public static final String ANDROID_DESIGNER_ID = "android-designer";

  public static boolean acceptLayout(final @NotNull Project project, final @NotNull VirtualFile file) {
    if (RenderService.NELE_ENABLED) {
      return false;
    }
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    return psiFile instanceof XmlFile &&
           AndroidFacet.getInstance(psiFile) != null &&
           LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return acceptLayout(project, file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new AndroidDesignerEditor(project, file);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return ANDROID_DESIGNER_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()
           ? FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
           : FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}