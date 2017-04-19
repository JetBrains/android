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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Creates a deep copy of {@link ProductFlavorContainer}.
 *
 * @see IdeAndroidProject
 */
public class IdeProductFlavorContainer extends IdeModel implements ProductFlavorContainer {
  @NotNull private final ProductFlavor myProductFlavor;
  @NotNull private final SourceProvider mySourceProvider;
  @NotNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

  public IdeProductFlavorContainer(@NotNull ProductFlavorContainer container, @NotNull ModelCache modelCache) {
    super(container, modelCache);
    myProductFlavor = modelCache.computeIfAbsent(container.getProductFlavor(), flavor -> new IdeProductFlavor(flavor, modelCache));
    mySourceProvider = modelCache.computeIfAbsent(container.getSourceProvider(), provider -> new IdeSourceProvider(provider, modelCache));
    myExtraSourceProviders = copy(container.getExtraSourceProviders(), modelCache,
                                  sourceProviderContainer -> new IdeSourceProviderContainer(sourceProviderContainer, modelCache));
  }

  @Override
  @NotNull
  public ProductFlavor getProductFlavor() {
    return myProductFlavor;
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Collection<SourceProviderContainer> getExtraSourceProviders() {
    return myExtraSourceProviders;
  }
}
