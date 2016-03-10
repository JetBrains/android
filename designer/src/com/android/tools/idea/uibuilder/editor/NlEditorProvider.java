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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.AndroidPsiUtils;
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
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;

public class NlEditorProvider implements FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the layout editor
   */
  private static final String DESIGNER_ID = "android-designer2";

  @Nullable
  private static AndroidFacet getFacet(@NonNull Project project, @NonNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    return psiFile instanceof XmlFile ? AndroidFacet.getInstance(psiFile) : null;
  }

  @Override
  public boolean accept(@NonNull Project project, @NonNull VirtualFile file) {
    if (getFacet(project, file) == null) {
      return false;
    }

    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }

    XmlFile xmlFile = (XmlFile)psiFile;
    return LayoutDomFileDescription.isLayoutFile(xmlFile) || MenuDomFileDescription.isMenuFile(xmlFile);
  }

  @NonNull
  @Override
  public FileEditor createEditor(@NonNull Project project, @NonNull VirtualFile file) {
    AndroidFacet facet = getFacet(project, file);
    assert facet != null; // checked by acceptLayout
    return new NlEditor(facet, file, project);
  }

  @Override
  public void disposeEditor(@NonNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NonNull
  @Override
  public FileEditorState readState(@NonNull Element sourceElement, @NonNull Project project, @NonNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@NonNull FileEditorState state, @NonNull Project project, @NonNull Element targetElement) {
  }

  @NonNull
  @Override
  public String getEditorTypeId() {
    return DESIGNER_ID;
  }

  @NonNull
  @Override
  public FileEditorPolicy getPolicy() {
    return AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()
           ? FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
           : FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
