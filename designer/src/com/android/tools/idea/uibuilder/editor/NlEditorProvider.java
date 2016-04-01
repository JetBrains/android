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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.model.ResourceType;
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

public class NlEditorProvider implements FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the layout editor
   */
  private static final String DESIGNER_ID = "android-designer2";

  @Nullable
  private static AndroidFacet getFacet(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    return psiFile instanceof XmlFile ? AndroidFacet.getInstance(psiFile) : null;
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (getFacet(project, file) == null) {
      return false;
    }

    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }

    XmlFile xmlFile = (XmlFile)psiFile;

    return ResourceType.LAYOUT.isResourceTypeOf(xmlFile) ||
           ResourceType.MENU.isResourceTypeOf(xmlFile) ||
           ResourceType.PREFERENCE_SCREEN.isResourceTypeOf(xmlFile);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    AndroidFacet facet = getFacet(project, file);
    assert facet != null; // checked by acceptLayout
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
