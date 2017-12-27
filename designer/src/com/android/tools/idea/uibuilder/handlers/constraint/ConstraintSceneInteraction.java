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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.surface.SceneView;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements constraintlayout-specific behaviour on an interaction
 */
public class ConstraintSceneInteraction extends SceneInteraction {

  @Nullable final private NlComponent myPrimary;

  /**
   * Base constructor
   *
   * @param sceneView the ScreenView we belong to
   */
  public ConstraintSceneInteraction(@NotNull SceneView sceneView, @NotNull NlComponent primary) {
    super(sceneView);
    myPrimary = primary;
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    if (!canceled && myPrimary != null) {
      ConstraintReferenceManagement.updateConstraints(myPrimary, mySceneView.getScene());
    }
  }
}
