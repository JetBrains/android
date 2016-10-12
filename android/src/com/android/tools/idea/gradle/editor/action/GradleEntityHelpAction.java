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
import com.google.common.base.Strings;
import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GradleEntityHelpAction extends AbstractGradleEntityAction {

  public GradleEntityHelpAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.Actions.Help);
    presentation.setText(CommonBundle.getHelpButtonText());
  }

  @Override
  protected void doActionPerformed(@NotNull GradleEditorEntity entity, AnActionEvent event) {
    String helpId = entity.getHelpId();
    if (Strings.isNullOrEmpty(helpId)) {
      return;
    }
    assert helpId != null;
    Project project = event.getProject();
    BrowserUtil.browse(helpId, project);
  }
}
