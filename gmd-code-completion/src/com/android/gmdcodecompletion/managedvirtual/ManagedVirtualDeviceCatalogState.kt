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
package com.android.gmdcodecompletion.managedvirtual

import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.GmdDeviceCatalogState
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.rd.util.Date

/** This class stores data that is persisted by ManagedVirtualDeviceCatalogService across idea applications */
data class ManagedVirtualDeviceCatalogState(
  @OptionTag(tag = "expireDate")
  override val expireDate: Date = Date(0),
  // Use custom converter for more complicated persistent component state
  @OptionTag(tag = "managedVirtualDeviceCatalog",
             converter = ManagedVirtualDeviceCatalogConverter::class)
  override val myDeviceCatalog: ManagedVirtualDeviceCatalog = ManagedVirtualDeviceCatalog()) : GmdDeviceCatalogState(
  expireDate, myDeviceCatalog) {

  internal class ManagedVirtualDeviceCatalogConverter : Converter<ManagedVirtualDeviceCatalog>() {
    override fun toString(value: ManagedVirtualDeviceCatalog): String = GmdDeviceCatalog.toJson(value)
    override fun fromString(value: String): ManagedVirtualDeviceCatalog = GmdDeviceCatalog.fromJson(value)
  }
}