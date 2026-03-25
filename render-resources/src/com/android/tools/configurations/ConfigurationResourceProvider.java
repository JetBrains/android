/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.res.ResourceRepositoryManager;
import org.jetbrains.annotations.Nullable;

class ConfigurationResourceProvider implements ResourceItemResolver.ResourceProvider {
  private final Configuration myConfig;

  ConfigurationResourceProvider(Configuration config) {
    this.myConfig = config;
  }

  @Override
  public @Nullable ResourceResolver getResolver(boolean createIfNecessary) {
    if (createIfNecessary) {
      return myConfig.getResourceResolver();
    }
    return myConfig.getSettings().getResolverCache().getCachedResourceResolver(
      myConfig.getTarget(), myConfig.getTheme(), myConfig.getFullConfig(), myConfig.getOverlays()
    );
  }

  @Override
  public @Nullable ResourceRepository getFrameworkResources() {
    ResourceRepositoryManager manager = myConfig.getConfigModule().getResourceRepositoryManager();
    return manager != null ? manager.getFrameworkResources(
      manager.getLanguagesInProject(), myConfig.getOverlays()
    ) : null;
  }

  @Override
  public @Nullable ResourceRepository getAppResources() {
    ResourceRepositoryManager manager = myConfig.getConfigModule().getResourceRepositoryManager();
    return manager != null ? manager.getAppResources() : null;
  }
}
