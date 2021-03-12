/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.actions;

import static com.android.tools.idea.ui.designer.DesignSurfaceNotificationManagerKt.NOTIFICATION_KEY;

import com.android.tools.idea.actions.DesignerActions;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.rendering.RenderUtils;
import com.android.tools.idea.ui.designer.DesignSurfaceNotificationManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class RefreshRenderAction extends AnAction {

  private RefreshRenderAction() {
  }

  @NotNull
  public static RefreshRenderAction getInstance() {
    return (RefreshRenderAction) ActionManager.getInstance().getAction(DesignerActions.ACTION_FORCE_REFRESH_PREVIEW);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (DesignerActionUtils.isActionEventFromJTextField(e)) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(e.getData(DesignerDataKeys.DESIGN_SURFACE) != null && e.getData(NOTIFICATION_KEY) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DesignSurface surface = e.getRequiredData(DesignerDataKeys.DESIGN_SURFACE);
    DesignSurfaceNotificationManager notification = e.getData(NOTIFICATION_KEY);
    RenderUtils.refreshRenderAndNotify(surface, notification);
  }
}
