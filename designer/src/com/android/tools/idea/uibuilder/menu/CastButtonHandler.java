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

import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.common.model.NlAttributesHolder;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public final class CastButtonHandler extends MenuHandler {
  static boolean handles(@NotNull NlAttributesHolder button) {
    return CLASS_MEDIA_ROUTE_ACTION_PROVIDER.isEquals(button.getAttribute(AUTO_URI, "actionProviderClass"));
  }

  @NotNull
  @Override
  public String getGradleCoordinateId(@NotNull String tagName) {
    return tagName.startsWith(ANDROIDX_PKG_PREFIX) ?
           AndroidxNameUtils.getCoordinateMapping(MEDIA_ROUTER_LIB_ARTIFACT) :
           MEDIA_ROUTER_LIB_ARTIFACT;
  }
}
