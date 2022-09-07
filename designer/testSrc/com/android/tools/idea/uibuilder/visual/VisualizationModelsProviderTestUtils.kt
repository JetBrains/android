/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.resources.NightMode
import com.android.resources.UiMode
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.AdaptiveIconShape
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.editors.theme.ThemeResolver
import com.android.tools.idea.uibuilder.LayoutTestCase
import org.jetbrains.android.sdk.CompatibilityRenderTarget
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget

fun verifyAdaptiveShapeReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  // If the first enum value of AdaptiveIconShape() is same as the current sourceConfig.adaptiveShape, then the
  // ConfigurationListener.CFG_ADAPTIVE_SHAPE will not be triggered and the change would not reflect to the models.
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, AdaptiveIconShape.values().toList(),
                        Configuration::setAdaptiveShape, Configuration::getAdaptiveShape)
}
fun verifyDeviceReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val manager = sourceConfig.configurationManager
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, manager.devices,
                        { device -> this.setDevice(device, false) }, { this.device })
}

fun verifyDeviceStateReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val device = sourceConfig.device ?: return
  val configsToVerify = modelsToVerify.map { it.configuration }

  for (state in device.allStates) {
    val stateName = state.name
    val configsShouldResponse = configsToVerify.filter {
      it.device?.allStates?.map { deviceState -> deviceState.name }?.contains(stateName) ?: false
    }

    sourceConfig.deviceState = state
    for (responseConfig in configsShouldResponse) {
      if (shouldReflect) {
        LayoutTestCase.assertEquals(responseConfig.deviceState!!.name, stateName)
      }
      else {
        LayoutTestCase.assertNotSame(responseConfig.deviceState!!.name, stateName)
      }
    }
  }
}

fun verifyUiModeReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, UiMode.values().toList(),
                        Configuration::setUiMode, Configuration::getUiMode)
}

fun verifyNightModeReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, NightMode.values().toList(),
                        Configuration::setNightMode, Configuration::getNightMode)
}

fun verifyThemeReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val themeNames = ThemeResolver(sourceConfig).recommendedThemes.map { it.resourceUrl.toString() }
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, themeNames,
                        Configuration::setTheme, Configuration::getTheme)
}

fun verifyTargetReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val manager = sourceConfig.configurationManager
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, manager.targets.toList(),
                        Configuration::setTarget, { this.target?.let { StudioEmbeddedRenderTarget.getCompatibilityTarget(it) } }) {
    a, b -> if (a is CompatibilityRenderTarget && b is CompatibilityRenderTarget) { a.hashString() == b.hashString() } else a == b
  }
}

fun verifyLocaleReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val manager = sourceConfig.configurationManager
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, manager.localesInProject,
                        Configuration::setLocale, Configuration::getLocale)
}

fun verifyFontReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean) {
  val fontScales = listOf(0.85f, 1.0f, 1.15f, 1.3f)
  verifyChangeReflected(sourceConfig, modelsToVerify, shouldReflect, fontScales,
                        Configuration::setFontScale, Configuration::getFontScale)
}

fun <T> verifyChangeReflected(sourceConfig: Configuration, modelsToVerify: Collection<NlModel>, shouldReflect: Boolean,
                              valueToTest: Iterable<T>, setValue: Configuration.(T) -> Unit, getValue: Configuration.() -> T?,
                              equalsFunc: ((T?, T?) -> Boolean)? = null) {
  // Using LayoutTestCase::assertEquals when custom equalsFunc is not given.
  // This keeps showing the error log in "Expected: ... ; Actual: ..." style when equalsFunc is not defined.
  val assertFunc: (T?, T?) -> Unit = if (equalsFunc == null) LayoutTestCase::assertEquals
  else { a: T?, b: T? -> LayoutTestCase.assertTrue(equalsFunc(a, b)) }

  if (!shouldReflect) {
    val origins = modelsToVerify.associateWith { model -> model.configuration.getValue() }
    for (value in valueToTest) {
      sourceConfig.setValue(value)
      modelsToVerify.forEach { model -> assertFunc(origins[model], model.configuration.getValue()) }
    }
  }
  else {
    for (value in valueToTest) {
      sourceConfig.setValue(value)
      // Sometimes configuration convert or change the actual value during the setter. Use the actual set value to verify.
      val valueAfterSet = sourceConfig.getValue()
      modelsToVerify.forEach { model -> assertFunc(valueAfterSet, model.configuration.getValue()) }
    }
  }
}


