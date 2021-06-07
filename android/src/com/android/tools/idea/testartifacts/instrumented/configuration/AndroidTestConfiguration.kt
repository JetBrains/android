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
package com.android.tools.idea.testartifacts.instrumented.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "AndroidTestConfiguration",
       storages = [Storage(value = "android-test-configuration.xml",
                           roamingType = RoamingType.DISABLED)])
data class AndroidTestConfiguration(
  // Run Android Instrumented Tests using Gradle.
  @JvmField
  var RUN_ANDROID_TEST_USING_GRADLE: Boolean = false,
) : PersistentStateComponent<AndroidTestConfiguration> {
  companion object {
    @JvmStatic
    fun getInstance(): AndroidTestConfiguration = ServiceManager.getService(AndroidTestConfiguration::class.java)
  }
  override fun getState(): AndroidTestConfiguration = this
  override fun loadState(state: AndroidTestConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
