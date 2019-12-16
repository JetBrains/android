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
package com.android.tools.idea.npw.module.recipes.thingsModule

import com.android.tools.idea.npw.module.recipes.IconsGenerationStyle
import com.android.tools.idea.npw.module.recipes.basicStylesXml
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.renderIf

fun RecipeExecutor.generateThingsModule(
  data: ModuleTemplateData,
  appTitle: String
) {
  generateCommonModule(
    data, appTitle,
    generateThingsManifest(data.packageName, !data.isLibrary),
    true,
    iconsGenerationStyle = IconsGenerationStyle.NONE,
    stylesXml = basicStylesXml("android:Theme.Material.Light.DarkActionBar"),
    addLintOptions = true
  )

  addDependency("com.google.android.things:androidthings:+", "provided")
}

fun generateThingsManifest(
  packageName: String,
  hasApplicationBlock: Boolean = false
): String {
  val applicationBlock = renderIf(hasApplicationBlock) {
    """
    <application android:label="@string/app_name">
        <uses-library android:name="com.google.android.things"/>
    </application>
    """
  }

  return """
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">
    $applicationBlock
    </manifest>
  """
}
