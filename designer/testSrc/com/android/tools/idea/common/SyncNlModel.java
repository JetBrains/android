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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link NlModel} that runs all the operations synchronously for testing
 */
public class SyncNlModel extends NlModel {

  private Configuration myConfiguration; // for testing purposes
  private DesignSurface mySurface; // for testing purposes

  @NotNull
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static SyncNlModel create(@NotNull DesignSurface surface,
                                   @Nullable Disposable parent,
                                   @NotNull AndroidFacet facet,
                                   @NotNull VirtualFile file) {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(facet);
    Configuration configuration =  manager.getConfiguration(file);
    configuration.setDevice(manager.getDeviceById("Nexus 4"), true);
    return new SyncNlModel(surface, parent, facet, file, configuration);
  }

  private SyncNlModel(@NotNull DesignSurface surface, @Nullable Disposable parent, @NotNull AndroidFacet facet, @NotNull VirtualFile file, @NotNull Configuration configuration) {
    super(parent, facet, file, configuration, surface.getComponentRegistrar());
    mySurface = surface;
  }

  @NotNull
  public DesignSurface getSurface() {
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
