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
package com.android.tools.idea.naveditor.scene;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Version of {@link ThumbnailManager} than can be used in bazel tests.
 */
public class TestableThumbnailManager extends ThumbnailManager {

  private final ThumbnailManager myPreviousManager;

  private TestableThumbnailManager(@NotNull AndroidFacet facet, @Nullable ThumbnailManager previousManager) {
    super(facet);
    myPreviousManager = previousManager;
  }

  public static void register(@NotNull AndroidFacet facet) {
    ThumbnailManager newInstance = new TestableThumbnailManager(facet, ThumbnailManager.getInstance(facet));
    ThumbnailManager.setInstance(facet, newInstance);
  }

  public void deregister() {
    ThumbnailManager.setInstance(getFacet(), myPreviousManager);
  }

  @Nullable
  @Override
  protected RenderTask createTask(@NotNull XmlFile file,
                                  @NotNull DesignSurface surface,
                                  @NotNull Configuration configuration,
                                  RenderService renderService,
                                  RenderLogger logger) {
    RenderTask task = super.createTask(file, surface, configuration, renderService, logger);
    if (task != null) {
      task.disableSecurityManager();
    }
    return task;
  }
}
