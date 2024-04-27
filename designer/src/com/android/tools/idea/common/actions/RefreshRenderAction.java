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

import com.android.tools.idea.actions.DesignerActions;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.rendering.RenderUtils;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlSupportedActions;
import com.android.tools.idea.uibuilder.surface.NlSupportedActionsKt;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
    DesignSurface<?> designSurface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    boolean enabled;
    if (designSurface != null) {
      enabled = !(designSurface instanceof NlDesignSurface)
                // If the surface is an NlDesignSurface we need to make sure the action is supported
                // since it can decide to disable it.
                || NlSupportedActionsKt.isActionSupported(designSurface, NlSupportedActions.REFRESH);
    } else {
      enabled = false;
    }

    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface == null) return;
    RenderUtils.clearCache(surface.getConfigurations());
    surface.forceUserRequestedRefresh();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
