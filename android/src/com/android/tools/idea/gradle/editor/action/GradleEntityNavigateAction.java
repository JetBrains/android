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
package com.android.tools.idea.gradle.editor.action;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.android.tools.idea.gradle.editor.entity.GradleEntityDefinitionValueLocationAware;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import org.jetbrains.annotations.NotNull;

public class GradleEntityNavigateAction extends AbstractGradleEntityAction {

  public GradleEntityNavigateAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.Actions.EditSource);
  }

  @Override
  protected void doActionPerformed(@NotNull GradleEditorEntity entity, AnActionEvent event) {
    if (entity instanceof GradleEntityDefinitionValueLocationAware) {
      GradleEditorSourceBinding location = ((GradleEntityDefinitionValueLocationAware)entity).getDefinitionValueLocation();
      if (location != null) {
        RangeMarker marker = location.getRangeMarker();
        if (marker.isValid()) {
          OpenFileDescriptor descriptor = new OpenFileDescriptor(location.getProject(), location.getFile(), marker.getStartOffset());
          if (descriptor.canNavigate()) {
            descriptor.navigate(true);
          }
        }
      }
    }
  }
}
