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
package com.android.tools.adtui.model;

import java.util.EventListener;

/**
 * Interface for handling events fired by {@link SelectionModel}.
 */
public interface SelectionListener extends EventListener {
  /**
   * Event fired when the user finishes creating a new selection (for example, the moment when the
   * user releases the mouse after dragging a new selection into existence).
   */
  default void selectionCreated() {}

  /**
   * Event fired when the user clears a selection (for example, the moment when the user presses
   * ESC to dismiss a focused selection).
   */
  default void selectionCleared() {}
}

