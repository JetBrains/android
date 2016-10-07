/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.grid;

import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.GRID_LAYOUT_LIB_ARTIFACT;

/**
 * Handler for the {@code <android.support.v7.widget.GridLayout>} layout from AppCompat
 */
public class GridLayoutV7Handler extends GridLayoutHandler {

  @Override
  @NotNull
  public String getGradleCoordinate(@NotNull String viewTag) {
    return GRID_LAYOUT_LIB_ARTIFACT;
  }
}
