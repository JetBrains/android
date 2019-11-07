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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.FormFactor
import java.io.File
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wizard.template.Recipe

typealias NewAndroidModuleRecipe = (String?) -> Recipe

interface ModuleTemplateGalleryEntry : ModuleGalleryEntry {
  /**
   * The file from where this template was loaded.
   * [newTemplate] should be used instead if [StudioFlags.NPW_NEW_MODULE_TEMPLATES] is enabled.
   */
  val templateFile: File?
  /**
   * A recipe to run (actually a closure which runs the recipe with additional parameters).
   * Used instead of [templateFile] if [StudioFlags.NPW_NEW_MODULE_TEMPLATES] is enabled.
   */
  val recipe: NewAndroidModuleRecipe?

  /**
   * Form factor associated with this template.
   */
  val formFactor: FormFactor
  /**
   * true if this template belongs to a Library.
   */
  val isLibrary: Boolean
}