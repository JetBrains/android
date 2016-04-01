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
package com.android.tools.idea.uibuilder.surface;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;

import java.util.List;

/**
 * Interface implemented by listeners for {@link DesignSurface} events
 */
public interface DesignSurfaceListener {
  /** The set of currently selected components in the given surface changed */
  void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection);

  /** The current screen, if any, changed */
  void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView);

  /** The current model changed */
  void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model);
}
