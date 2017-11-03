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
package com.android.tools.idea.uibuilder.handlers.absolute;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class AbsoluteDragTarget extends DragBaseTarget {

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_X, String.format(VALUE_N_DP, Math.max(x - myComponent.getParent().getDrawX(), 0)));
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_Y, String.format(VALUE_N_DP, Math.max(y - myComponent.getParent().getDrawY(), 0)));
  }
}
