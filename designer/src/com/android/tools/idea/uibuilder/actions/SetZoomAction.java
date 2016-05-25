/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action for performing a zooming operation according to the {@link ZoomType}
 */
public class SetZoomAction extends AnAction {
  @NotNull private final DesignSurface mySurface;
  @NotNull private final ZoomType myType;

  public SetZoomAction(@NotNull DesignSurface surface, @NotNull ZoomType type) {
    super(type.getLabel());
    myType = type;
    mySurface = surface;
    getTemplatePresentation().setIcon(type.getIcon());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    mySurface.zoom(myType);
  }
}
