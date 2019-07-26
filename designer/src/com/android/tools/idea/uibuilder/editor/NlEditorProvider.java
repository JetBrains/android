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

import com.android.tools.idea.common.editor.DesignerEditor;
import com.android.tools.idea.common.editor.DesignerEditorProvider;
import com.android.tools.idea.common.type.DesignerTypeRegistrar;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class NlEditorProvider extends DesignerEditorProvider {

  public NlEditorProvider() {
    super(ImmutableList.of(LayoutFileType.INSTANCE, MenuFileType.INSTANCE, PreferenceScreenFileType.INSTANCE));
    if (!StudioFlags.NELE_SPLIT_EDITOR.get()) {
      // When not using the split editor, we should register the files that otherwise would be accepted/registered by BorderlessNlEditor.
      DesignFilesPreviewEditorProviderKt.acceptedTypes().forEach(DesignerTypeRegistrar.INSTANCE::register);
    }
  }

  @NotNull
  @Override
  public DesignerEditor createDesignEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new NlEditor(file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return NlEditorKt.NL_EDITOR_ID;
  }
}
