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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.common.editor.DesignerEditorProvider;
import com.android.tools.idea.common.type.DesignerTypeRegistrar;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.uibuilder.type.AdaptativeIconFileType;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType;
import com.android.tools.idea.uibuilder.type.StateListFileType;
import com.android.tools.idea.uibuilder.type.VectorFileType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

// TODO(b/120407029): move this to uibuilder module.
public class NlEditorProvider extends DesignerEditorProvider {

  @NotNull
  private final List<DesignerEditorFileType> myRegisteredTypes;

  public NlEditorProvider() {
    myRegisteredTypes = ImmutableList.of(AdaptativeIconFileType.INSTANCE, LayoutFileType.INSTANCE, MenuFileType.INSTANCE,
                                         PreferenceScreenFileType.INSTANCE, StateListFileType.INSTANCE, VectorFileType.INSTANCE);
    myRegisteredTypes.forEach(DesignerTypeRegistrar.INSTANCE::register);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new NlEditor(file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return NlEditorKt.NL_EDITOR_ID;
  }

  @Override
  protected boolean acceptAndroidFacetXml(@NotNull XmlFile xmlFile) {
    return myRegisteredTypes.stream().anyMatch(type -> type instanceof LayoutEditorFileType && type.isResourceTypeOf(xmlFile));
  }
}
