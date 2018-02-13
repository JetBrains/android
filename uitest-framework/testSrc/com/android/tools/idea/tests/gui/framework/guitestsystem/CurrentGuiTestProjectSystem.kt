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
package com.android.tools.idea.tests.gui.framework.guitestsystem

import com.android.tools.idea.tests.gui.framework.IdeTestApplication
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.GuiTestProjectSystem
import org.junit.rules.ExternalResource

/**
 * Caches the ui test project system to use based on the build system specified by [IdeTestApplication]'s
 * target build system. This allows the test runner to compute and cache this information just once per test.
 */
class CurrentGuiTestProjectSystem : ExternalResource() {
  lateinit var testProjectSystem: GuiTestProjectSystem
    private set

  override fun before() {
    val buildSystem = IdeTestApplication.getInstance().targetBuildSystem ?:
        throw IllegalStateException("TargetBuildSystem has not been set.")
    testProjectSystem = GuiTestProjectSystem.forBuildSystem(buildSystem) ?:
        throw IllegalStateException("No build system delegate found for $buildSystem.")
    testProjectSystem.validateSetup()
  }
}