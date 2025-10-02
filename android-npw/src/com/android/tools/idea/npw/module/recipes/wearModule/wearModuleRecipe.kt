/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.wearModule

import com.android.tools.idea.npw.module.recipes.IconsGenerationStyle
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

private const val WATCH_FEATURE_BLOCK =
  // language=XML
  """
    <uses-feature android:name="android.hardware.type.watch" />
  """

fun RecipeExecutor.generateWearModule(
  data: ModuleTemplateData,
  appTitle: String?,
  useKts: Boolean,
  useVersionCatalog: Boolean = true,
) {
  if (data.isWatchFace) {
    generateWearWatchFaceModule(data, appTitle, useKts, useVersionCatalog)
    return
  }
  generateCommonModule(
    data,
    appTitle,
    useKts,
    generateManifest(
      hasApplicationBlock = !data.isLibrary,
      theme = "@android:style/Theme.DeviceDefault",
      usesFeatureBlock = WATCH_FEATURE_BLOCK,
      hasRoundIcon = false,
    ),
    iconsGenerationStyle = IconsGenerationStyle.ALL,
    themesXml = null,
    colorsXml = null,
    noKtx = true,
    useVersionCatalog = useVersionCatalog,
  )

  addDependency("com.google.android.gms:play-services-wearable:+")
}

private fun RecipeExecutor.generateWearWatchFaceModule(
  data: ModuleTemplateData,
  appTitle: String?,
  useKts: Boolean,
  useVersionCatalog: Boolean = true,
) {
  generateCommonModule(
    data = data,
    appTitle = appTitle,
    appTitleResName = "watch_face_name",
    useKts = useKts,
    manifestXml =
      generateManifest(hasApplicationBlock = false, usesFeatureBlock = WATCH_FEATURE_BLOCK),
    iconsGenerationStyle = IconsGenerationStyle.NONE,
    themesXml = null,
    colorsXml = null,
    noKtx = true,
    useVersionCatalog = useVersionCatalog,
  )
}
