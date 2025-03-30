/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.navigation.PsFlavorDimensionNavigationPath
import icons.StudioIcons.Misc.PRODUCT_FLAVOR_DIMENSION
import java.util.Objects
import javax.swing.Icon

class PsFlavorDimension(
  override val parent: PsAndroidModule,
  val isInvalid: Boolean = false
) : PsChildModel() {
  var parsedName: String? = null

  internal fun init(parsedName: String) {
    this.parsedName = parsedName
  }

  override val name get() = if (isInvalid) "(invalid)" else parsedName.orEmpty()
  override val path: PsFlavorDimensionNavigationPath get() = PsFlavorDimensionNavigationPath(parent.path.productFlavorsPath, name)
  override val isDeclared: Boolean get() = parsedName != null
  override val icon: Icon = PRODUCT_FLAVOR_DIMENSION

  override fun equals(other: Any?) = when(other) {
    is PsFlavorDimension -> if (this.isInvalid && other.isInvalid) this.parent == other.parent else super.equals(other)
    else -> false
  }
  override fun hashCode() = if (isInvalid) Objects.hash(42, parent) else super.hashCode()
}
