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
import com.android.tools.idea.gradle.editor.ui.GradleEditorUiConstants;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractGradleEntityAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    GradleEditorEntity entity = GradleEditorUiConstants.ACTIVE_ENTITY_KEY.getData(e.getDataContext());
    if (entity != null) {
      doActionPerformed(entity, e);
    }
  }

  protected abstract void doActionPerformed(@NotNull GradleEditorEntity entity, AnActionEvent event);
}
