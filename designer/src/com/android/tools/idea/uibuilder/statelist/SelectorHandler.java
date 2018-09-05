/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.statelist;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public final class SelectorHandler extends ViewGroupHandler {
  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return Arrays.asList(
      "constantSize",
      "dither",
      "variablePadding");
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    switch (component.getTagName()) {
      case SdkConstants.TAG_SELECTOR:
        return StudioIcons.LayoutEditor.Menu.MENU;
      case SdkConstants.TAG_ITEM:
        return StudioIcons.LayoutEditor.Menu.ITEM;
      default:
        return super.getIcon(component);
    }
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    // The default behaviour of a ViewHandler is to add the "Expand horizontally" and "Expand vertically" actions.
    // This does not make sense for state lists, so instead no action is added to the toolbar
  }
}