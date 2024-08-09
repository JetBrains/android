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
import com.intellij.util.xml.DefinesXml
import com.intellij.util.xml.Required
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.Styleable
import org.jetbrains.android.dom.converters.AndroidPackageConverter

@DefinesXml
interface Queries : ManifestElement {
  fun getProviders(): List<Provider>

  fun getIntents(): List<Intent>

  fun getPackages(): List<Package>

  @DefinesXml
  interface Package : AndroidDomElement {
    @Attribute("name")
    @Convert(AndroidPackageConverter::class)
    fun getName(): AndroidAttributeValue<String>
  }

  @DefinesXml
  interface Intent : AndroidDomElement {
    @Required fun getAction(): Action

    fun getData(): Data

    fun getCategory(): Category

    /**
     * Data is redefined here because some of the "AndroidManifestData" styleable's attributes are
     * not applicable in this context per
     * https://developer.android.com/training/package-visibility/declaring#intent-filter-signature.
     */
    @DefinesXml
    @Styleable(
      "AndroidManifestData",
      skippedAttributes = ["path", "pathPrefix", "pathPattern", "port", "mimeGroup"],
    )
    interface Data : AndroidDomElement {}
  }

  @DefinesXml
  interface Provider : AndroidDomElement {
    @Attribute("authorities")
    @Convert(AndroidPackageConverter::class)
    fun getAuthorities(): AndroidAttributeValue<String>
  }
}
