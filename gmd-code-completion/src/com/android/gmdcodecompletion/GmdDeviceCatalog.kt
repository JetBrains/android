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
package com.android.gmdcodecompletion

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.Logger

/** Defines basic behavior of device catalogs for GMD code completion
 *  ManagedVirtualDeviceCatalog and FtlDeviceCatalog inherits this abstract class
 */
abstract class GmdDeviceCatalog {
  companion object {
    internal fun toJson(catalog: GmdDeviceCatalog): String =
      GsonBuilder().create().toJson(catalog).replace(Regex("(?<!\\\\)\""), "'")

    inline fun <reified T : GmdDeviceCatalog> fromJson(json: String): T {
      return try {
        GsonBuilder().create().fromJson(json, T::class.java)
      }
      // Local cache might be corrupted and thus fail to reconstruct GmdDeviceCatalog children classes
      catch (e: JsonSyntaxException) {
        Logger.getInstance(T::class.java).warn("Invalid state JSON string: '$json'")
        T::class.java.getConstructor().newInstance()
      }
    }
  }

  protected var isEmptyCatalog: Boolean = true
  fun isEmpty(): Boolean = this.isEmptyCatalog

  // Make sure every GMD device catalog check if all its fields are empty
  abstract fun checkEmptyFields(): GmdDeviceCatalog
  abstract fun syncDeviceCatalog(): GmdDeviceCatalog
}