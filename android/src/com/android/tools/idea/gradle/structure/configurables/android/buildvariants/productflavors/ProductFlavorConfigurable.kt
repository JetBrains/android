// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.ContainerConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors.ProductFlavorConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.listPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.mapPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class ProductFlavorConfigurable(
  private val productFlavor: PsProductFlavor,
  val context: PsContext
) :
  ChildModelConfigurable<PsProductFlavor, ProductFlavorConfigPanel>(productFlavor) {
  override fun getBannerSlogan() = "Product Flavor '${productFlavor.name}'"
  override fun getIcon(expanded: Boolean): Icon = productFlavor.icon
  override fun createPanel(): ProductFlavorConfigPanel = ProductFlavorConfigPanel(productFlavor, context)
}

class FlavorDimensionConfigurable(
    private val module: PsAndroidModule,
    val flavorDimension: PsFlavorDimension,
    val context: PsContext
) : NamedConfigurable<PsFlavorDimension>(), ContainerConfigurable<PsProductFlavor> {
  override fun getEditableObject(): PsFlavorDimension = flavorDimension
  override fun getBannerSlogan(): String = "Dimension '$flavorDimension'"
  override fun getIcon(expanded: Boolean): Icon = flavorDimension.icon
  override fun isModified(): Boolean = false
  override fun getDisplayName(): String = flavorDimension.name
  override fun apply() = Unit
  override fun setDisplayName(name: String?) = throw UnsupportedOperationException()

  override fun getChildrenModels(): Collection<PsProductFlavor> =
    module
      .productFlavors
      .filter { it.effectiveDimension == flavorDimension.name || (it.effectiveDimension == null && flavorDimension.isInvalid) }

  override fun createChildConfigurable(model: PsProductFlavor): NamedConfigurable<PsProductFlavor> =
    ProductFlavorConfigurable(model, context).also { Disposer.register(this, it) }
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.productFlavors.onChange(disposable, listener)
  override fun dispose() = Unit

  private val component = JPanel()
  override fun createOptionsPanel(): JComponent = component
}

fun productFlavorPropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.dimension, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_DIMENSION),
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationId, ::simplePropertyEditor,
                                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_APPLICATION_ID)
      else null,
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix, ::simplePropertyEditor, null) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionCode, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_VERSION_CODE),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionName, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_VERSION_NAME),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionNameSuffix, ::simplePropertyEditor, null),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_TARGET_SDK_VERSION),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.minSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_MIN_SDK_VERSION),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.signingConfig, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_SIGNING_CONFIG),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      if (isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.consumerProGuardFiles, ::listPropertyEditor, null) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.proGuardFiles, ::listPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_PROGUARD_FILES),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.manifestPlaceholders, ::mapPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_MANIFEST_PLACEHOLDERS),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_MULTI_DEX_ENABLED),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.resConfigs, ::listPropertyEditor, null),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_TEST_INSTRUMENTATION_RUNNER_CLASS_NAME),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments, ::mapPropertyEditor, null),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest, ::simplePropertyEditor, null),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling, ::simplePropertyEditor, null),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testApplicationId, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_BUILDVARIANTS_FLAVORS_TEST_APPLICATION_ID),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks, ::listPropertyEditor, null)))

