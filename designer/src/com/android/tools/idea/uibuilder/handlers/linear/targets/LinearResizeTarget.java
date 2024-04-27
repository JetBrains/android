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
package com.android.tools.idea.uibuilder.handlers.linear.targets;

import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.uibuilder.handlers.absolute.AbsoluteResizeTarget;
import com.android.sdklib.AndroidDpCoordinate;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget.Type.*;

/**
 * Target to handle the resizing of LinearLayout's children
 */
public class LinearResizeTarget extends AbsoluteResizeTarget {

  public LinearResizeTarget(@NotNull Type type) {
    super(type);
  }

  @Override
  protected void updateAttributes(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {

    Type t = getType();
    if (TOP == t || LEFT_TOP == t || RIGHT_TOP == t) {
      updateHeight(attributes, getNewHeight(y));
    }

    if (BOTTOM == t || LEFT_BOTTOM == t || RIGHT_BOTTOM == t) {
      updateHeight(attributes, getNewHeight(y));
    }

    if (LEFT == t || LEFT_BOTTOM == t || LEFT_TOP == t) {
      updateWidth(attributes, getNewWidth(x));
    }

    if (RIGHT == t || RIGHT_BOTTOM == t || RIGHT_TOP == t) {
      updateWidth(attributes, getNewWidth(x));
    }
  }
}
