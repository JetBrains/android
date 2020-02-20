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
package com.android.tools.idea.npw.module.recipes.tvModule

import com.android.tools.idea.npw.module.recipes.IconsGenerationStyle
import com.android.tools.idea.npw.module.recipes.basicStylesXml
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generateTvModule(
  data: ModuleTemplateData,
  appTitle: String,
  useKts: Boolean
) {
  generateCommonModule(
    data, appTitle, useKts,
    generateManifest(data.packageName, !data.isLibrary, hasRoundIcon = false),
    iconsGenerationStyle = IconsGenerationStyle.MIPMAP_SQUARE_ONLY,
    stylesXml = basicStylesXml("@style/Theme.Leanback"), colorsXml = null
  )

  addDependency("com.android.support:leanback-v17:+")
}
