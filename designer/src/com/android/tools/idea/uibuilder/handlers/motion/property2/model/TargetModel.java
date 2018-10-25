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
package com.android.tools.idea.uibuilder.handlers.motion.property2.model;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Model for supplying data to a TargetComponent.
 */
public class TargetModel {
  private final NlComponent myComponent;
  private final String myLabel;

  public TargetModel(@NotNull NlComponent component, @NotNull String label) {
    myComponent = component;
    myLabel = label;
  }

  @Nullable
  public Icon getComponentIcon() {
    ViewHandlerManager manager = ViewHandlerManager.get(myComponent.getModel().getProject());
    ViewHandler handler = manager.getHandler(myComponent);
    if (handler == null) {
      return StudioIcons.LayoutEditor.Palette.VIEW;
    }
    return handler.getIcon(myComponent);
  }

  @NotNull
  public String getComponentName() {
    String id = myComponent.getId();
    return id != null ? id : "<unnamed>";
  }

  @NotNull
  public String getElementDescription() {
    return myLabel;
  }
}
