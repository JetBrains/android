/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the ion functionLicense");
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

import com.android.ide.common.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.essentiallyEquals
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.modulePath
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths

class LegacyAndroidProjectTest(): AndroidGradleTestCase() {
  @Throws(Exception::class)
  fun testLegacyAndroidProjectFacade() {
    loadProject(TestProjectPaths.TEST_NOSYNCBUILDER)
    val appModule = getModule("app")

    val oldAndroidProject = AndroidModuleModel.get(appModule)!!.androidProject
    val newAndroidProject = NewAndroidProject(oldAndroidProject, modulePath)

    val oldVariant = oldAndroidProject.variants.first()!! as IdeVariant
    val newVariant = NewVariant(oldVariant, oldAndroidProject)
    val restoredOldAndroidProject = LegacyAndroidProject(newAndroidProject, newVariant)

    assertTrue(restoredOldAndroidProject essentiallyEquals oldAndroidProject)
  }
}