/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;

import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContentLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManagerKt;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * {@link PositionableContentLayoutManager} for the {@link NlDesignSurface}. It uses a delegated {@link SurfaceLayoutManager} to layout the
 * the different {@link PositionableContent}s.
 * The {@link SurfaceLayoutManager} can be switch at runtime.
 */
public class NlDesignSurfacePositionableContentLayoutManager extends PositionableContentLayoutManager implements LayoutManagerSwitcher {
  @NotNull private final NlDesignSurface myDesignSurface;
  @NotNull private SurfaceLayoutManager myLayoutManager;

  NlDesignSurfacePositionableContentLayoutManager(@NotNull NlDesignSurface surface, @NotNull SurfaceLayoutManager defaultLayoutManager) {
    myDesignSurface = surface;
    myLayoutManager = defaultLayoutManager;
  }

  @Override
  public void layoutContainer(@NotNull Collection<? extends PositionableContent> content, @NotNull Dimension availableSize) {
    availableSize = myDesignSurface.getExtentSize();
    SurfaceLayoutManagerKt.layout(myLayoutManager, content, availableSize.width, availableSize.height, myDesignSurface.isCanvasResizing());
  }

  @NotNull
  @Override
  public Dimension preferredLayoutSize(@NotNull Collection<? extends PositionableContent> content, @NotNull Dimension availableSize) {
    availableSize = myDesignSurface.getExtentSize();
    Dimension dimension = myLayoutManager.getRequiredSize(content, availableSize.width, availableSize.height, null);
    dimension.setSize(
      Math.max(myDesignSurface.getScrollableViewMinSize().width, dimension.width),
      Math.max(myDesignSurface.getScrollableViewMinSize().height, dimension.height)
    );

    return dimension;
  }

  @NotNull
  SurfaceLayoutManager getLayoutManager() {
    return myLayoutManager;
  }

  @Override
  public boolean isLayoutManagerSelected(@NotNull SurfaceLayoutManager manager) {
    return myLayoutManager.equals(manager);
  }

  @Override
  public void setLayoutManager(@NotNull SurfaceLayoutManager manager, @NotNull DesignSurface.SceneViewAlignment sceneViewAlignment) {
    myLayoutManager = manager;
    myDesignSurface.setSceneViewAlignment(sceneViewAlignment);
    myDesignSurface.setScrollPosition(0, 0);
    myDesignSurface.revalidateScrollArea();
  }


  @NotNull
  @Override
  public Map<PositionableContent, Point> getMeasuredPositionableContentPosition(@NotNull Collection<? extends PositionableContent> content,
                                                                                int availableWidth,
                                                                                int availableHeight) {
    return myLayoutManager.measure(content, availableWidth, availableHeight, myDesignSurface.isCanvasResizing());
  }
}
