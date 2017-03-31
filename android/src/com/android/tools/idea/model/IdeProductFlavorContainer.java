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
package com.android.tools.idea.model;

import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;

/**
 * Creates a deep copy of {@link ProductFlavorContainer}.
 *
 * @see IdeAndroidProject
 */
public class IdeProductFlavorContainer implements ProductFlavorContainer, Serializable {

  @NotNull private final ProductFlavor myProductFlavor;
  @NotNull private final SourceProvider mySourceProvider;
  @NotNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

  public IdeProductFlavorContainer(@NotNull ProductFlavorContainer original) {
    myProductFlavor = new IdeProductFlavor(original.getProductFlavor());
    mySourceProvider = new IdeSourceProvider(original.getSourceProvider());

    Collection<SourceProviderContainer> orExtraSourceProvider = original.getExtraSourceProviders();
    myExtraSourceProviders = new ArrayList<>();
    for (SourceProviderContainer container : orExtraSourceProvider) {
      myExtraSourceProviders.add(new IdeSourceProviderContainer(container));
    }
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
