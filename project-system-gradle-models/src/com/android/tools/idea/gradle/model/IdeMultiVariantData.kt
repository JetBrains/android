/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

interface IdeMultiVariantData {

  /**
   * Returns the [IdeProductFlavor] for the 'main' default config.
   *
   * @return the product flavor.
   */
  val defaultConfig: IdeProductFlavor

  /**
   * Returns a list of all the [IdeBuildType] in their container.
   *
   * @return a list of build type containers.
   */
  val buildTypes: Collection<IdeBuildTypeContainer>

  /**
   * Returns a list of all the [IdeProductFlavor] in their container.
   *
   * @return a list of product flavor containers.
   */
  val productFlavors: Collection<IdeProductFlavorContainer>
}