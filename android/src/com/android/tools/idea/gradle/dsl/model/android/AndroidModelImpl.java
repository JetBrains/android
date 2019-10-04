/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import static com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement.VIEW_BINDING_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.COMPILE_OPTIONS_BLOCK_NAME;

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.DataBindingModel;
import com.android.tools.idea.gradle.dsl.api.android.DexOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.SplitsModel;
import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.ViewBindingModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AndroidModelImpl extends GradleDslBlockModel implements AndroidModel {
  @NonNls private static final String NDK_VERSION = "ndkVersion";
  @NonNls private static final String BUILD_TOOLS_VERSION = "buildToolsVersion";
  @NonNls private static final String COMPILE_SDK_VERSION = "compileSdkVersion";
  @NonNls public static final String DEFAULT_CONFIG = "defaultConfig";
  @NonNls private static final String DEFAULT_PUBLISH_CONFIG = "defaultPublishConfig";
  @NonNls private static final String DYNAMIC_FEATURES = "dynamicFeatures";
  @NonNls private static final String FLAVOR_DIMENSIONS = "flavorDimensions";
  @NonNls private static final String GENERATE_PURE_SPLITS = "generatePureSplits";
  @NonNls private static final String PUBLISH_NON_DEFAULT = "publishNonDefault";
  @NonNls private static final String RESOURCE_PREFIX = "resourcePrefix";

  // TODO: Add support for useLibrary

  public AndroidModelImpl(@NotNull AndroidDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public AaptOptionsModel aaptOptions() {
    AaptOptionsDslElement aaptOptionsElement = myDslElement.getPropertyElement(AAPT_OPTIONS_BLOCK_NAME, AaptOptionsDslElement.class);
    if (aaptOptionsElement == null) {
      aaptOptionsElement = new AaptOptionsDslElement(myDslElement);
      myDslElement.setNewElement(aaptOptionsElement);
    }
    return new AaptOptionsModelImpl(aaptOptionsElement);
  }

  @Override
  @NotNull
  public AdbOptionsModel adbOptions() {
    AdbOptionsDslElement adbOptionsElement = myDslElement.getPropertyElement(ADB_OPTIONS_BLOCK_NAME, AdbOptionsDslElement.class);
    if (adbOptionsElement == null) {
      adbOptionsElement = new AdbOptionsDslElement(myDslElement);
      myDslElement.setNewElement(adbOptionsElement);
    }
    return new AdbOptionsModelImpl(adbOptionsElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel buildToolsVersion() {
    return getModelForProperty(BUILD_TOOLS_VERSION);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel ndkVersion() {
    return getModelForProperty(NDK_VERSION);
  }

    @NotNull
  @Override
  public List<BuildTypeModel> buildTypes() {
    BuildTypesDslElement buildTypes = myDslElement.getPropertyElement(BUILD_TYPES_BLOCK_NAME, BuildTypesDslElement.class);
    return buildTypes == null ? ImmutableList.of() : buildTypes.get();
  }

  @NotNull
  @Override
  public BuildTypeModel addBuildType(@NotNull String buildType) {
    BuildTypesDslElement buildTypes = myDslElement.getPropertyElement(BUILD_TYPES_BLOCK_NAME, BuildTypesDslElement.class);
    if (buildTypes == null) {
      buildTypes = new BuildTypesDslElement(myDslElement);
      myDslElement.setNewElement(buildTypes);
    }

    BuildTypeDslElement buildTypeElement = buildTypes.getPropertyElement(buildType, BuildTypeDslElement.class);
    if (buildTypeElement == null) {
      buildTypeElement = new BuildTypeDslElement(buildTypes, GradleNameElement.create(buildType));
      buildTypes.setNewElement(buildTypeElement);
    }
    return new BuildTypeModelImpl(buildTypeElement);
  }

  @Override
  public void removeBuildType(@NotNull String buildType) {
    BuildTypesDslElement buildTypes = myDslElement.getPropertyElement(BUILD_TYPES_BLOCK_NAME, BuildTypesDslElement.class);
    if (buildTypes != null) {
      buildTypes.removeProperty(buildType);
    }
  }

  @NotNull
  @Override
  public CompileOptionsModel compileOptions() {
    CompileOptionsDslElement element = myDslElement.getPropertyElement(COMPILE_OPTIONS_BLOCK_NAME, CompileOptionsDslElement.class);
    if (element == null) {
      element = new CompileOptionsDslElement(myDslElement);
      myDslElement.setNewElement(element);
    }
    return new CompileOptionsModelImpl(element, false);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel compileSdkVersion() {
    return getModelForProperty(COMPILE_SDK_VERSION);
  }

  @Override
  @NotNull
  public DataBindingModel dataBinding() {
    DataBindingDslElement dataBindingElement = myDslElement.getPropertyElement(DATA_BINDING_BLOCK_NAME, DataBindingDslElement.class);
    if (dataBindingElement == null) {
      dataBindingElement = new DataBindingDslElement(myDslElement);
      myDslElement.setNewElement(dataBindingElement);
    }
    return new DataBindingModelImpl(dataBindingElement);
  }

  @Override
  @NotNull
  public ProductFlavorModel defaultConfig() {
    ProductFlavorDslElement defaultConfigElement = myDslElement.getPropertyElement(DEFAULT_CONFIG, ProductFlavorDslElement.class);
    if (defaultConfigElement == null) {
      defaultConfigElement = new ProductFlavorDslElement(myDslElement, GradleNameElement.create(DEFAULT_CONFIG));
      myDslElement.setNewElement(defaultConfigElement);
    }
    return new ProductFlavorModelImpl(defaultConfigElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel defaultPublishConfig() {
    return getModelForProperty(DEFAULT_PUBLISH_CONFIG);
  }

  @Override
  @NotNull
  public DexOptionsModel dexOptions() {
    DexOptionsDslElement dexOptionsElement = myDslElement.getPropertyElement(DEX_OPTIONS_BLOCK_NAME, DexOptionsDslElement.class);
    if (dexOptionsElement == null) {
      dexOptionsElement = new DexOptionsDslElement(myDslElement);
      myDslElement.setNewElement(dexOptionsElement);
    }
    return new DexOptionsModelImpl(dexOptionsElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel dynamicFeatures() {
    return getModelForProperty(DYNAMIC_FEATURES, false);
  }

  @NotNull
  @Override
  public ExternalNativeBuildModel externalNativeBuild() {
    ExternalNativeBuildDslElement externalNativeBuildDslElement = myDslElement.getPropertyElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME,
                                                                                                  ExternalNativeBuildDslElement.class);
    if (externalNativeBuildDslElement == null) {
      externalNativeBuildDslElement = new ExternalNativeBuildDslElement(myDslElement);
      myDslElement.setNewElement(externalNativeBuildDslElement);
    }
    return new ExternalNativeBuildModelImpl(externalNativeBuildDslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel flavorDimensions() {
    return getModelForProperty(FLAVOR_DIMENSIONS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel generatePureSplits() {
    return getModelForProperty(GENERATE_PURE_SPLITS);
  }

  @Override
  @NotNull
  public LintOptionsModel lintOptions() {
    LintOptionsDslElement lintOptionsDslElement = myDslElement.getPropertyElement(LINT_OPTIONS_BLOCK_NAME, LintOptionsDslElement.class);
    if (lintOptionsDslElement == null) {
      lintOptionsDslElement = new LintOptionsDslElement(myDslElement);
      myDslElement.setNewElement(lintOptionsDslElement);
    }
    return new LintOptionsModelImpl(lintOptionsDslElement);
  }

  @Override
  @NotNull
  public PackagingOptionsModel packagingOptions() {
    PackagingOptionsDslElement packagingOptionsDslElement =
      myDslElement.getPropertyElement(PACKAGING_OPTIONS_BLOCK_NAME, PackagingOptionsDslElement.class);
    if (packagingOptionsDslElement == null) {
      packagingOptionsDslElement = new PackagingOptionsDslElement(myDslElement);
      myDslElement.setNewElement(packagingOptionsDslElement);
    }
    return new PackagingOptionsModelImpl(packagingOptionsDslElement);
  }

  @Override
  @NotNull
  public List<ProductFlavorModel> productFlavors() {
    ProductFlavorsDslElement productFlavors = myDslElement.getPropertyElement(PRODUCT_FLAVORS_BLOCK_NAME, ProductFlavorsDslElement.class);
    return productFlavors == null ? ImmutableList.of() : productFlavors.get();
  }

  @Override
  @NotNull
  public ProductFlavorModel addProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors = myDslElement.getPropertyElement(PRODUCT_FLAVORS_BLOCK_NAME, ProductFlavorsDslElement.class);
    if (productFlavors == null) {
      productFlavors = new ProductFlavorsDslElement(myDslElement);
      myDslElement.setNewElement(productFlavors);
    }

    ProductFlavorDslElement flavorElement = productFlavors.getPropertyElement(flavor, ProductFlavorDslElement.class);
    if (flavorElement == null) {
      GradleNameElement name = GradleNameElement.create(flavor);
      flavorElement = new ProductFlavorDslElement(productFlavors, name);
      productFlavors.setNewElement(flavorElement);
    }
    return new ProductFlavorModelImpl(flavorElement);
  }

  @Override
  public void removeProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors = myDslElement.getPropertyElement(PRODUCT_FLAVORS_BLOCK_NAME, ProductFlavorsDslElement.class);
    if (productFlavors != null) {
      productFlavors.removeProperty(flavor);
    }
  }

  @Override
  @NotNull
  public List<SigningConfigModel> signingConfigs() {
    SigningConfigsDslElement signingConfigs = myDslElement.getPropertyElement(SIGNING_CONFIGS_BLOCK_NAME, SigningConfigsDslElement.class);
    return signingConfigs == null ? ImmutableList.of() : signingConfigs.get();
  }

  @Override
  @NotNull
  public SigningConfigModel addSigningConfig(@NotNull String config) {
    SigningConfigsDslElement signingConfigs = myDslElement.getPropertyElement(SIGNING_CONFIGS_BLOCK_NAME, SigningConfigsDslElement.class);
    if (signingConfigs == null) {
      signingConfigs = new SigningConfigsDslElement(myDslElement);
      myDslElement.addNewElementAt(0, signingConfigs);
    }

    SigningConfigDslElement configElement = signingConfigs.getPropertyElement(config, SigningConfigDslElement.class);
    if (configElement == null) {
      GradleNameElement name = GradleNameElement.create(config);
      configElement = new SigningConfigDslElement(signingConfigs, name);
      signingConfigs.setNewElement(configElement);
    }
    return new SigningConfigModelImpl(configElement);
  }

  @Override
  public void removeSigningConfig(@NotNull String configName) {
    SigningConfigsDslElement signingConfig = myDslElement.getPropertyElement(SIGNING_CONFIGS_BLOCK_NAME, SigningConfigsDslElement.class);
    if (signingConfig != null) {
      signingConfig.removeProperty(configName);
    }
  }

  @Override
  @NotNull
  public List<SourceSetModel> sourceSets() {
    SourceSetsDslElement sourceSets = myDslElement.getPropertyElement(SOURCE_SETS_BLOCK_NAME, SourceSetsDslElement.class);
    return sourceSets == null ? ImmutableList.of() : sourceSets.get();
  }

  @Override
  @NotNull
  public SourceSetModel addSourceSet(@NotNull String sourceSet) {
    SourceSetsDslElement sourceSets = myDslElement.getPropertyElement(SOURCE_SETS_BLOCK_NAME, SourceSetsDslElement.class);
    if (sourceSets == null) {
      sourceSets = new SourceSetsDslElement(myDslElement);
      myDslElement.setNewElement(sourceSets);
    }

    SourceSetDslElement sourceSetElement = sourceSets.getPropertyElement(sourceSet, SourceSetDslElement.class);
    if (sourceSetElement == null) {
      GradleNameElement name = GradleNameElement.create(sourceSet);
      sourceSetElement = new SourceSetDslElement(sourceSets, name);
      sourceSets.setNewElement(sourceSetElement);
    }
    return new SourceSetModelImpl(sourceSetElement);
  }

  @Override
  public void removeSourceSet(@NotNull String sourceSet) {
    SourceSetsDslElement sourceSets = myDslElement.getPropertyElement(SOURCE_SETS_BLOCK_NAME, SourceSetsDslElement.class);
    if (sourceSets != null) {
      sourceSets.removeProperty(sourceSet);
    }
  }

  @Override
  @NotNull
  public SplitsModel splits() {
    SplitsDslElement splitsDslElement = myDslElement.getPropertyElement(SPLITS_BLOCK_NAME, SplitsDslElement.class);
    if (splitsDslElement == null) {
      splitsDslElement = new SplitsDslElement(myDslElement);
      myDslElement.setNewElement(splitsDslElement);
    }
    return new SplitsModelImpl(splitsDslElement);
  }

  @Override
  @NotNull
  public TestOptionsModel testOptions() {
    TestOptionsDslElement testOptionsDslElement = myDslElement.getPropertyElement(TEST_OPTIONS_BLOCK_NAME, TestOptionsDslElement.class);
    if (testOptionsDslElement == null) {
      testOptionsDslElement = new TestOptionsDslElement(myDslElement);
      myDslElement.setNewElement(testOptionsDslElement);
    }
    return new TestOptionsModelImpl(testOptionsDslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel publishNonDefault() {
    return getModelForProperty(PUBLISH_NON_DEFAULT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resourcePrefix() {
    return getModelForProperty(RESOURCE_PREFIX);
  }

  @Override
  @NotNull
  public ViewBindingModel viewBinding() {
    ViewBindingDslElement viewBindingElement = myDslElement.getPropertyElement(VIEW_BINDING_BLOCK_NAME, ViewBindingDslElement.class);
    if (viewBindingElement == null) {
      viewBindingElement = new ViewBindingDslElement(myDslElement);
      myDslElement.setNewElement(viewBindingElement);
    }
    return new ViewBindingModelImpl(viewBindingElement);
  }
}
