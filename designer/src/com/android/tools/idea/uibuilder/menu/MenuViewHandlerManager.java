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
package com.android.tools.idea.uibuilder.menu;

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlLayoutType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MenuViewHandlerManager {
  private MenuViewHandlerManager() {
  }

  @Nullable
  public static ViewHandler getHandler(@NotNull NlComponent component) {
    if (!component.getModel().getType().equals(NlLayoutType.MENU)) {
      return null;
    }

    if (CastButtonHandler.handles(component)) {
      return new CastButtonHandler();
    }

    if (SearchItemHandler.handles(component)) {
      return new SearchItemHandler();
    }

    if (SwitchItemHandler.handles(component)) {
      return new SwitchItemHandler();
    }

    if (ItemHandler.handles(component)) {
      return new ItemHandler();
    }

    return null;
  }
}
