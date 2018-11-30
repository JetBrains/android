/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.common.editor.DesignerEditorProvider;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.common.type.DesignerTypeRegistrar;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription;
import org.jetbrains.annotations.NotNull;

public class NavEditorProvider extends DesignerEditorProvider {

  @NotNull
  private static final NavigationFileType SUPPORTED_FILE_TYPE = new NavigationFileType();

  public NavEditorProvider() {
    DesignerTypeRegistrar.INSTANCE.register(SUPPORTED_FILE_TYPE);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new NavEditor(file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return NavEditorKt.NAV_EDITOR_ID;
  }

  @Override
  protected boolean acceptAndroidFacetXml(@NotNull XmlFile xmlFile) {
    return SUPPORTED_FILE_TYPE.isResourceTypeOf(xmlFile);
  }

  private static class NavigationFileType implements DesignerEditorFileType {

    @Override
    public boolean isResourceTypeOf(@NotNull PsiFile file) {
      return file instanceof XmlFile && NavigationDomFileDescription.isNavFile((XmlFile)file);
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new NavToolbarActionGroups(surface);
    }

    @Override
    public boolean isEditable() {
      return true;
    }
  }
}
