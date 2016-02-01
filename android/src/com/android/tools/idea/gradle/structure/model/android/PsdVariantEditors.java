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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class PsdVariantEditors {
  @NotNull private final Map<String, PsdVariantEditor> myVariantEditorsByName = Maps.newHashMap();

  PsdVariantEditors(@NotNull PsdAndroidModuleEditor parent) {
    for (Variant variant : parent.getGradleModel().getAndroidProject().getVariants()) {
      List<String> productFlavorEditors = Lists.newArrayList();
      for (String productFlavor : variant.getProductFlavors()) {
        PsdProductFlavorEditor productFlavorEditor = parent.findProductFlavorEditor(productFlavor);
        if (productFlavorEditor != null) {
          productFlavorEditors.add(productFlavorEditor.getName());
        }
        else {
          // TODO handle case when product flavor is not found.
        }
      }

      PsdVariantEditor variantEditor = new PsdVariantEditor(parent, variant.getName(), productFlavorEditors, variant);
      myVariantEditorsByName.put(variant.getName(), variantEditor);
    }
  }

  @Nullable
  PsdVariantEditor find(@NotNull String variantName) {
    return myVariantEditorsByName.get(variantName);
  }

  @NotNull
  Collection<PsdVariantEditor> getValues() {
    return myVariantEditorsByName.values();
  }
}
