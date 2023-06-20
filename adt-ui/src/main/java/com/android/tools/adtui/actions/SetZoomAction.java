/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions;

import com.android.tools.adtui.Zoomable;
import com.android.tools.adtui.ZoomableKt;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action for performing a zooming operation according to the {@link ZoomType}
 */
abstract public class SetZoomAction extends AnAction {
  @VisibleForTesting
  @NotNull
  public final ZoomType myType;

  public SetZoomAction(@NotNull ZoomType type) {
    super(type.getLabel());
    myType = type;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Zoomable zoomable = event.getData(ZoomableKt.ZOOMABLE_KEY);
    if (zoomable != null) {
      zoomable.zoom(myType);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(myType.getIcon());
    e.getPresentation().setText(myType.getLabel());

    String optionalDescription = myType.getDescription();

    if (optionalDescription != null) {
      e.getPresentation().setDescription(optionalDescription);
    }
  }
}
