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

import com.android.tools.idea.uibuilder.handlers.assistant.RecyclerViewAssistant;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistant;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;

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
    return RECYCLER_VIEW_LIB_ARTIFACT;
  }

  @Nullable
  @Override
  public ComponentAssistant.PanelFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (component.getAttribute(TOOLS_URI, ATTR_LISTITEM) != null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(component.getModel().getModule());
    if (facet == null) {
      return null;
    }

    return RecyclerViewAssistant::new;
  }
}
