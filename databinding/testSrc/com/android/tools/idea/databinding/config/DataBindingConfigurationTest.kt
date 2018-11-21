/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataBindingConfigurationTest {
  @Test
  fun legacyConfigurationIsMigrated() {
    run { // legacy config has CODE_NAVIGATION_MODE set to CODE
      val sourceConfig = DataBindingConfiguration()
      sourceConfig.CODE_NAVIGATION_MODE = DataBindingConfiguration.CodeNavigationMode.CODE
      assertThat(sourceConfig.CODE_GEN_MODE).isEqualTo(DataBindingConfiguration.CodeGenMode.IN_MEMORY)

      val destConfig = DataBindingConfiguration()
      destConfig.loadState(sourceConfig)
      assertThat(destConfig.CODE_NAVIGATION_MODE).isEqualTo(DataBindingConfiguration.CodeNavigationMode.DEFAULT)
      assertThat(destConfig.CODE_GEN_MODE).isEqualTo(DataBindingConfiguration.CodeGenMode.ON_DISK)
    }

    run { // legacy config has CODE_NAVIGATION_MODE set to XML
      val sourceConfig = DataBindingConfiguration()
      sourceConfig.CODE_NAVIGATION_MODE = DataBindingConfiguration.CodeNavigationMode.XML
      assertThat(sourceConfig.CODE_GEN_MODE).isEqualTo(DataBindingConfiguration.CodeGenMode.IN_MEMORY)

      val destConfig = DataBindingConfiguration()
      destConfig.loadState(sourceConfig)
      assertThat(destConfig.CODE_NAVIGATION_MODE).isEqualTo(DataBindingConfiguration.CodeNavigationMode.DEFAULT)
      assertThat(destConfig.CODE_GEN_MODE).isEqualTo(DataBindingConfiguration.CodeGenMode.IN_MEMORY)
    }
  }
}