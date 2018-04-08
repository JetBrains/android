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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantActionTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.assistant.TransitionLayoutAssistantPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_POSITION;

public class MotionLayoutHandler extends ConstraintLayoutHandler {

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_POSITION);
  }

  @Nullable
  private static ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get() || !SdkConstants.MOTION_LAYOUT.isEquals(component.getTagName())) {
      return null;
    }

    return (context) -> new TransitionLayoutAssistantPanel(surface, context.getComponent(), context.getDoClose());
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    ComponentAssistantFactory panelFactory = getComponentAssistant(sceneComponent.getScene().getDesignSurface(), sceneComponent.getNlComponent());

    return panelFactory != null ?
           ImmutableList.of(new ComponentAssistantActionTarget(panelFactory)) :
           ImmutableList.of();
  }

  @Override
  public boolean needsAccessoryPanel(@NotNull AccessoryPanel.Type type) {
    switch (type) {
      case SOUTH_PANEL:
      case EAST_PANEL:
        return true;
    }
    return false;
  }

  @Override
  @NotNull
  public AccessoryPanelInterface createAccessoryPanel(@NotNull DesignSurface surface,
                                                      @NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility panelVisibility) {
    switch (type) {
      case SOUTH_PANEL: return new MotionLayoutTimelinePanel(surface, parent, panelVisibility);
      case EAST_PANEL: return new MotionLayoutAttributePanel(parent, panelVisibility);
    }
     throw new IllegalArgumentException("Unsupported type");
  }

  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return new MotionLayoutSceneInteraction(screenView, component);
  }
}
