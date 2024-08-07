/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.mendel

import com.intellij.openapi.extensions.ExtensionPointName

interface MendelFlagsProvider {
  fun getActiveExperimentIds(): Collection<Int>

  companion object {
    val EP_NAME: ExtensionPointName<MendelFlagsProvider> =
      ExtensionPointName.create("com.android.tools.idea.mendelFlagsProvider")

    fun isExperimentEnabled(id: Int) =
      EP_NAME.extensionList.any {
        id in it.getActiveExperimentIds()
      }

    fun getInstances() = EP_NAME.extensionList
  }
}