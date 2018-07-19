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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.TestOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldTestOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldTestOptionsExecution

open class LegacyTestOptions(private val testOptions: TestOptions) : OldTestOptions {
  override fun getExecution(): OldTestOptionsExecution = OldTestOptionsExecution.valueOf(testOptions.execution.name)
  override fun toString(): String = "LegacyBaseArtifact{$execution}"

  override fun getAnimationsDisabled(): Boolean = throw UnusedModelMethodException("getAnimationsDisabled")
}

class LegacyTestOptionsStub(testOptions: TestOptions) : LegacyTestOptions(testOptions) {
  override fun getAnimationsDisabled(): Boolean = false
}
