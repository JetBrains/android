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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.createNewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.modulePath
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.testConverter
import junit.framework.TestCase

class NewAndroidProjectTest : TestCase() {
  private val newAndroidProject = createNewAndroidProject()

  @Throws(Exception::class)
  fun testNewAndroidProjectCaching() {
    val androidProjectProto = newAndroidProject.toProto(testConverter)
    val restoredAndroidProject = NewAndroidProject(androidProjectProto, modulePath, testConverter)
    val restoredAndroidProjectProto = restoredAndroidProject.toProto(testConverter)

    assertEquals(newAndroidProject, restoredAndroidProject)
    assertEquals(androidProjectProto, restoredAndroidProjectProto)
  }
}