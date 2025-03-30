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
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.SceneView;
import org.jetbrains.annotations.NotNull;

/**
 * Implements constraintlayout-specific behaviour on an interaction
 */
public class ConstraintSceneInteraction extends SceneInteraction {

  @NotNull final private NlComponent myPrimary;

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
  public void commit(@NotNull InteractionEvent event) {
    super.commit(event);
    ConstraintReferenceManagement.updateConstraints(myPrimary, mySceneView.getScene());
  }
}
