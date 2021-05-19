/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.sdklib.devices.Abi

data class VariantAbi(val variant: String, val abi: String) {
  @Transient
  val displayName: String = "$variant-$abi"

  companion object {
    @JvmStatic
    fun fromString(variantAbiString: String): VariantAbi? {
      for (abi in Abi.values()) {
        if (variantAbiString.endsWith(abi.toString())) {
          val abiName = abi.toString()
          val variantName = variantAbiString.substring(0, variantAbiString.length - 1 - abi.toString().length)
          return VariantAbi(variantName, abiName)
        }
      }
      return null
    }
  }
}