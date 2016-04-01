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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;

public class DnDTransferComponent {
  private final String myTag;
  private final String myRepresentation;
  @AndroidCoordinate private final int myWidth;
  @AndroidCoordinate private final int myHeight;

  public DnDTransferComponent(@NotNull String tag,
                              @NotNull String representation,
                              @AndroidCoordinate int width,
                              @AndroidCoordinate int height) {
    myTag = tag;
    myRepresentation = representation;
    myWidth = width;
    myHeight = height;
  }

  @NotNull
  public String getTag() {
    return myTag;
  }

  @NotNull
  public String getRepresentation() {
    return myRepresentation;
  }

  @AndroidCoordinate
  public int getWidth() {
    return myWidth;
  }

  @AndroidCoordinate
  public int getHeight() {
    return myHeight;
  }
}
