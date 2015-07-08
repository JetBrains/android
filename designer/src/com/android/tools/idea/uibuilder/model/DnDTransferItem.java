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

import java.util.Collections;
import java.util.List;

public class DnDTransferItem {
  private final boolean myFromPalette;
  private final long myModelId;
  private final List<DnDTransferComponent> myComponents;

  /**
   * Create a drag and drop item for a new component from the palette.
   */
  public DnDTransferItem(DnDTransferComponent component) {
    this(true, 0, Collections.singletonList(component));
  }

  /**
   * Create a drag and drop item for existing designer components.
   */
  public DnDTransferItem(long modelId, List<DnDTransferComponent> components) {
    this(false, modelId, components);
  }

  private DnDTransferItem(boolean fromPalette, long modelId, List<DnDTransferComponent> components) {
    myFromPalette = fromPalette;
    myModelId = modelId;
    myComponents = components;
  }

  public boolean isFromPalette() {
    return myFromPalette;
  }

  public long getModelId() {
    return myModelId;
  }

  public List<DnDTransferComponent> getComponents() {
    return myComponents;
  }
}
