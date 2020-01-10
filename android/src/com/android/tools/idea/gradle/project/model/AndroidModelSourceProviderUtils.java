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
package com.android.tools.idea.gradle.project.model;

import static com.intellij.util.ArrayUtil.contains;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AndroidModelSourceProviderUtils {
  private static final Logger LOG = Logger.getInstance(AndroidModelSourceProviderUtils.class);

  @NotNull
  public static List<SourceProvider> getMainSourceProviders(AndroidModuleModel model, @NotNull String variantName) {
    Variant variant = model.myVariantsByName.get(variantName);
    if (variant == null) {
      LOG.error("Unknown variant name '" + variantName + "' found in the module '" + model.getModuleName() + "'");
      return ImmutableList.of();
    }

    List<SourceProvider> providers = new ArrayList<>();
    // Main source provider.
    providers.add(model.getDefaultSourceProvider());
    // Flavor source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = model.findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }

    // Multi-flavor source provider.
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    SourceProvider multiFlavorProvider = mainArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorProvider != null) {
      providers.add(multiFlavorProvider);
    }

    // Build type source provider.
    BuildTypeContainer buildType = model.findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.add(buildType.getSourceProvider());

    // Variant  source provider.
    SourceProvider variantProvider = mainArtifact.getVariantSourceProvider();
    if (variantProvider != null) {
      providers.add(variantProvider);
    }

    return providers;
  }

  @NotNull
  public static List<SourceProvider> getTestSourceProviders(AndroidModuleModel model,
                                                            @NotNull String variantName,
                                                            @NotNull String... testArtifactNames) {
    validateTestArtifactNames(testArtifactNames);

    // Collect the default config test source providers.
    Collection<SourceProviderContainer> extraSourceProviders = model.getAndroidProject().getDefaultConfig().getExtraSourceProviders();
    List<SourceProvider> providers = new ArrayList<>(getSourceProvidersForArtifacts(extraSourceProviders, testArtifactNames));

    Variant variant = model.myVariantsByName.get(variantName);
    assert variant != null;

    // Collect the product flavor test source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = model.findProductFlavor(flavor);
      assert productFlavor != null;
      providers.addAll(getSourceProvidersForArtifacts(productFlavor.getExtraSourceProviders(), testArtifactNames));
    }

    // Collect the build type test source providers.
    BuildTypeContainer buildType = model.findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.addAll(getSourceProvidersForArtifacts(buildType.getExtraSourceProviders(), testArtifactNames));

    // TODO: Does it make sense to add multi-flavor test source providers?
    // TODO: Does it make sense to add variant test source providers?
    return providers;
  }

  private static void validateTestArtifactNames(@NotNull String[] testArtifactNames) {
    for (String name : testArtifactNames) {
      if (!isTestArtifact(name)) {
        String msg = String.format("'%1$s' is not a test artifact", name);
        throw new IllegalArgumentException(msg);
      }
    }
  }

  @NotNull
  public static List<SourceProvider> getAllSourceProviders(AndroidModuleModel model) {
    Collection<Variant> variants = model.getAndroidProject().getVariants();
    List<SourceProvider> providers = new ArrayList<>();

    // Add main source set
    providers.add(model.getDefaultSourceProvider());

    // Add all flavors
    Collection<ProductFlavorContainer> flavors = model.getAndroidProject().getProductFlavors();
    for (ProductFlavorContainer flavorContainer : flavors) {
      providers.add(flavorContainer.getSourceProvider());
    }

    // Add the multi-flavor source providers
    for (Variant variant : variants) {
      SourceProvider provider = variant.getMainArtifact().getMultiFlavorSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    // Add all the build types
    Collection<BuildTypeContainer> buildTypes = model.getAndroidProject().getBuildTypes();
    for (BuildTypeContainer btc : buildTypes) {
      providers.add(btc.getSourceProvider());
    }

    // Add all the variant source providers
    for (Variant variant : variants) {
      SourceProvider provider = variant.getMainArtifact().getVariantSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    return providers;
  }

  @NotNull
  public static Collection<SourceProvider> getSourceProvidersForArtifacts(@NotNull Iterable<SourceProviderContainer> containers,
                                                                          @NotNull String... artifactNames) {
    Set<SourceProvider> providers = new LinkedHashSet<>();
    for (SourceProviderContainer container : containers) {
      for (String artifactName : artifactNames) {
        if (artifactName.equals(container.getArtifactName())) {
          providers.add(container.getSourceProvider());
          break;
        }
      }
    }
    return providers;
  }

  private static boolean isTestArtifact(@Nullable String artifactName) {
    return contains(artifactName, AndroidModuleModel.TEST_ARTIFACT_NAMES);
  }
}
