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
package com.android.tools.idea.common;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Consumer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link NlModel} that runs all the operations synchronously for testing
 */
public class SyncNlModel extends NlModel {

  private Configuration myConfiguration; // for testing purposes
  private DesignSurface<? extends SceneManager> mySurface; // for testing purposes

  @NotNull
  public static SyncNlModel create(@Nullable Disposable parent,
                                   @NotNull Consumer<NlComponent> componentRegistrar,
                                   @Nullable String tooltip,
                                   @NotNull AndroidFacet facet,
                                   @NotNull VirtualFile file) {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(facet.getModule());
    Configuration configuration =  manager.getConfiguration(file);
    configuration.setDevice(manager.getDeviceById("Nexus 4"), true);
    return new SyncNlModel(parent, componentRegistrar, tooltip, facet, file, configuration);
  }

  @NotNull
  public static SyncNlModel create(@Nullable Disposable parent,
                                   @NotNull Consumer<NlComponent> componentRegistrar,
                                   @Nullable String tooltip,
                                   @NotNull AndroidFacet facet,
                                   @NotNull VirtualFile file,
                                   @NotNull Configuration configuration) {
    return new SyncNlModel(parent, componentRegistrar, tooltip, facet, file, configuration);
  }

  private SyncNlModel(@Nullable Disposable parent, @NotNull Consumer<NlComponent> componentRegistrar,
                      @Nullable String tooltip, @NotNull AndroidFacet facet, @NotNull VirtualFile file,
                      @NotNull Configuration configuration) {
    super(parent, tooltip, facet, file, configuration, componentRegistrar, DataContext.EMPTY_CONTEXT);
  }

  /**
   * FIXME(b/194482298): Needs to be removed after refactor. {@link NlModel} shouldn't have any information about {@link DesignSurface}.
   */
  @Deprecated
  public void setDesignSurface(@NotNull DesignSurface<? extends SceneManager> surface) {
    mySurface = surface;
  }

  /**
   * FIXME(b/194482298): Needs to be removed after refactor. {@link NlModel} shouldn't have any information about {@link DesignSurface}.
   */
  @Deprecated
  @NotNull
  public DesignSurface<? extends SceneManager> getSurface() {
    return mySurface;
  }

  @VisibleForTesting
  public void setConfiguration(Configuration configuration) {
    myConfiguration = configuration;
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    if (myConfiguration != null) {
      return myConfiguration;
    }
    return super.getConfiguration();
  }
}
