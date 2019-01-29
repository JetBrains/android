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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.createNewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.gradleproject.toProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.testConverter
import junit.framework.TestCase

class NewGradleProjectTest : TestCase() {
  private val newGradleProject = createNewGradleProject()

  @Throws(Exception::class)
  fun testNewGradleProjectCaching() {
    val gradleProjectProto = newGradleProject.toProto(testConverter)
    val restoredGradleProject = NewGradleProject(gradleProjectProto, testConverter)
    val restoredGradleProjectProto = restoredGradleProject.toProto(testConverter)

    assertEquals(newGradleProject, restoredGradleProject)
    assertEquals(gradleProjectProto, restoredGradleProjectProto)
  }
}