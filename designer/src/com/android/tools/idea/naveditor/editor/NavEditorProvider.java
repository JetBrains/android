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
import com.android.tools.idea.common.model.NlLayoutType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class NavEditorProvider extends DesignerEditorProvider {

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new NavEditor(file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return NavEditor.NAV_EDITOR_ID;
  }

  @Override
  protected boolean acceptAndroidFacetXml(@NotNull XmlFile xmlFile) {
    return NlLayoutType.typeOf(xmlFile) == NlLayoutType.NAV;
  }
}
