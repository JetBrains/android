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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.intellij.conversion.ConversionContext
import com.intellij.conversion.ProjectConverter
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.write
import java.nio.file.Files
import org.jetbrains.jps.model.serialization.JDomSerializationUtil

const val MISC_XML = "misc.xml"
const val NAME = "name"

class RulesPersistentStateConverter(private val context: ConversionContext) : ProjectConverter() {

  override fun isConversionNeeded(): Boolean {
    // Hack: ConverterProvider requires user input to change any settings. We modify the misc.xml
    // file here silently and return false to show that we do not want any conversion while doing
    // it silently
    val miscXml = context.settingsBaseDir?.resolve(MISC_XML) ?: return false
    if (!Files.exists(miscXml)) {
      return false
    }
    val element = JDOMUtil.load(miscXml)
    JDomSerializationUtil.findComponent(element, NETWORK_INSPECTOR_RULES)?.attributes?.forEach {
      if (it.name == NAME) {
        it.value = "deprecated$NETWORK_INSPECTOR_RULES"
      }
    }
    miscXml.write(JDOMUtil.write(element))
    return false
  }
}
