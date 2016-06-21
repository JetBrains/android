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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;

public class NlEditorProvider implements FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the layout editor
   */
  public static final String DESIGNER_ID = "android-designer2";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    AndroidFacet facet = psiFile instanceof XmlFile ? AndroidFacet.getInstance(psiFile) : null;
    if (facet == null) {
      return false;
    }

    // The preview editor currently works best with Gradle (see: b/29447486, and b/28110820), but we want to have support for
    // legacy android projects as well. Only enable for those two cases for now.
    if (!Projects.isBuildWithGradle(facet.getModule()) && !Projects.isLegacyIdeaAndroidModule(facet.getModule())) {
      return false;
    }

    return NlLayoutType.supports((XmlFile)psiFile);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    AndroidFacet facet = psiFile instanceof XmlFile ? AndroidFacet.getInstance(psiFile) : null;
    assert facet != null; // checked by accept
    return new NlEditor(facet, file, project);
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
    return DESIGNER_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()
           ? FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
           : FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
