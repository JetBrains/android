/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RegistryKeyRule
import com.intellij.testFramework.RuleChain
import com.intellij.ui.jcef.JBCefApp
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AndroidStudioJCefTest {

  @get:Rule
  val rules = RuleChain(ApplicationRule(), RegistryKeyRule("ide.browser.jcef.headless.enabled", true))

  @Before
  fun setUp() {
    assumeThat(IdeInfo.getInstance().isAndroidStudio).isTrue()
  }

  @Test
  fun androidStudioDoesNotCurrentlySupportJCef() {
    assertEquals(false, JBCefApp.isSupported() )
  }
}