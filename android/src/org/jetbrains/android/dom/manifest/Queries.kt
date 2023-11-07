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
package org.jetbrains.android.dom.manifest

import com.intellij.util.xml.Attribute
import com.intellij.util.xml.Convert
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.converters.AndroidPackageConverter

interface Queries : ManifestElement {
  fun getProviders(): List<Provider>

  fun getIntents(): List<Intent>

  fun getPackages(): List<Package>

  interface Package : AndroidDomElement {
    @Attribute("name")
    @Convert(AndroidPackageConverter::class)
    fun getName(): AndroidAttributeValue<String>
  }

  interface Intent : AndroidDomElement {
    fun getAction(): Action

    fun getData(): Data

    fun getCategory(): Category
  }

  interface Provider : AndroidDomElement {
    @Attribute("authorities")
    @Convert(AndroidPackageConverter::class)
    fun getAuthorities(): AndroidAttributeValue<String>
  }
}
