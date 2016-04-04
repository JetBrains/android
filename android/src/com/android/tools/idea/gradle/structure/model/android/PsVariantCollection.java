/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class PsVariantCollection implements PsModelCollection<PsVariant> {
  @NotNull private final Map<String, PsVariant> myVariantsByName = Maps.newHashMap();

  PsVariantCollection(@NotNull PsAndroidModule parent) {
    Collection<Variant> resolvedVariants = parent.getGradleModel().getAndroidProject().getVariants();

    for (Variant resolvedVariant : resolvedVariants) {
      List<String> productFlavors = Lists.newArrayList();
      for (String productFlavorName : resolvedVariant.getProductFlavors()) {
        PsProductFlavor productFlavor = parent.findProductFlavor(productFlavorName);
        if (productFlavor != null) {
          productFlavors.add(productFlavor.getName());
        }
        else {
          // TODO handle case when product flavor is not found.
        }
      }

      PsVariant variant = new PsVariant(parent, resolvedVariant.getName(), productFlavors, resolvedVariant);
      myVariantsByName.put(resolvedVariant.getName(), variant);
    }
  }

  @Override
  @Nullable
  public <S extends PsVariant> S findElement(@NotNull String name, @NotNull Class<S> type) {
    PsVariant found = myVariantsByName.get(name);
    return type.isInstance(found) ? type.cast(found) : null;
  }

  @Override
  public void forEach(@NotNull Predicate<PsVariant> function) {
    myVariantsByName.values().forEach(function::apply);
  }
}
