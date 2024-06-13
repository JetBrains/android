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
package com.android.tools.idea.testing

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
//import org.jetbrains.kotlin.idea.base.plugin.checkKotlinPluginMode
import org.junit.rules.ExternalResource

class KotlinPluginRule(private val pluginKind: KotlinPluginMode) : ExternalResource() {
  private var oldPropertyValue: String? = null
  override fun before() {
    oldPropertyValue = System.getProperty(PROPERTY_NAME)
    System.setProperty(PROPERTY_NAME, (pluginKind == KotlinPluginMode.K2).toString())
  }

  override fun after() {
    //checkKotlinPluginMode(pluginKind)

    val value = oldPropertyValue
    if (value != null) {
      System.setProperty(PROPERTY_NAME, value)
    } else {
      System.clearProperty(PROPERTY_NAME)
    }
    oldPropertyValue = null
  }

  companion object {
    private const val PROPERTY_NAME = "idea.kotlin.plugin.use.k2"
  }
}