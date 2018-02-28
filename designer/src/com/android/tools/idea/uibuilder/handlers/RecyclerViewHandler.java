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
package com.android.tools.idea.uibuilder.handlers;

import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantActionTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.assistant.RecyclerViewAssistant;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.flags.StudioFlags.NELE_WIDGET_ASSISTANT;

/**
 * Handler for the {@code <RecyclerView>} layout
 */
public class RecyclerViewHandler extends ViewGroupHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SCROLLBARS,
      ATTR_LISTITEM,
      ATTR_BACKGROUND,
      ATTR_CLIP_TO_PADDING,
      ATTR_CLIP_CHILDREN);
  }

  @Override
  @NotNull
  public String getGradleCoordinateId(@NotNull String tagName) {
    return tagName.startsWith(ANDROIDX_PKG_PREFIX) ?
           AndroidxNameUtils.getCoordinateMapping(RECYCLER_VIEW_LIB_ARTIFACT) :
           RECYCLER_VIEW_LIB_ARTIFACT;
  }

  @Nullable
  @Override
  public ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!NELE_WIDGET_ASSISTANT.get()) {
      return null;
    }

    if (component.getAttribute(TOOLS_URI, ATTR_LISTITEM) != null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(component.getModel().getModule());
    if (facet == null) {
      return null;
    }

    return RecyclerViewAssistant::createComponent;
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    ComponentAssistantFactory panelFactory = getComponentAssistant(sceneComponent.getScene().getDesignSurface(), sceneComponent.getNlComponent());

    return panelFactory != null ?
           ImmutableList.of(new ComponentAssistantActionTarget(panelFactory)) :
           ImmutableList.of();
  }
}
