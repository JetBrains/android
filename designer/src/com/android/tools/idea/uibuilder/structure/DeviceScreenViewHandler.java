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
package com.android.tools.idea.uibuilder.structure;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class DeviceScreenViewHandler extends ViewHandler {
  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    return AndroidIcons.Views.DeviceScreen;
  }

  @NotNull
  @Override
  public String getTitle(@NotNull NlComponent component) {
    for (NlComponent child : component.getChildren()) {
      String shownIn = child.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_SHOW_IN);

      if (shownIn != null) {
        return "Shown in " + shownIn;
      }
    }

    return "Device Screen";
  }
}
