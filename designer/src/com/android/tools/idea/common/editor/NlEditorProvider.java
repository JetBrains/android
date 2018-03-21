/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.editor;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlLayoutType;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class NlEditorProvider implements FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the layout editor
   */
  public static final String DESIGNER_ID = "android-designer2";

  /**
   * Name of the class that handles the quick definition feature in IntelliJ.
   * This class should be used by quick definition only.
   */
  private static final String CALLER_NAME_OF_QUICK_DEFINITION = ImplementationViewComponent.class.getName();

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    Object psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
    return psiFile instanceof XmlFile && NlLayoutType.supports((XmlFile)psiFile);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new NlEditor(file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return DESIGNER_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    if (AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()) {
      return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
    if (Arrays.stream(Thread.currentThread().getStackTrace())
      .anyMatch(element -> CALLER_NAME_OF_QUICK_DEFINITION.equals(element.getClassName()))) {
      // This function is called by quick definition, make the default editor as preferred editor.
      // This is a hack fixed for http://b/37050828
      return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }

    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
