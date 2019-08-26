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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action for performing a zooming operation according to the {@link ZoomType}
 */
public class SetZoomAction extends AnAction {
  @NotNull private final ZoomType myType;

  public SetZoomAction(@NotNull ZoomType type) {
    super(type.getLabel());
    myType = type;
    getTemplatePresentation().setIcon(type.getIcon());
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
    if (e.getPlace().contains("Surface")) {
      // Use floating set of icons for zoom actions on the Design Surface. To make the distinction from the usual editor toolbars, we check
      // for 'Surface' in the place to cover for expected names like 'NlSurfaceLayoutToolbar' instead of 'NlLayoutToolbar'.
      e.getPresentation().setIcon(myType.getFloatingIcon());
    }
  }
}
