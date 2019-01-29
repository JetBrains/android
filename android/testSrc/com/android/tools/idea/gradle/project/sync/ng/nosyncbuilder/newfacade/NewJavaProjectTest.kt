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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.createNewJavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject.NewJavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.testConverter
import junit.framework.TestCase

class NewJavaProjectTest : TestCase() {
  private val newJavaProject = createNewJavaProject()

  @Throws(Exception::class)
  fun testNewJavaProjectCaching() {
    val javaProjectProto = newJavaProject.toProto(testConverter)
    val restoredJavaProject = NewJavaProject(javaProjectProto, testConverter)
    val restoredJavaProto = restoredJavaProject.toProto(testConverter)

    assertEquals(newJavaProject.name, restoredJavaProject.name)
    assertEquals(newJavaProject.mainSourceSet, restoredJavaProject.mainSourceSet)
    assertEquals(newJavaProject.testSourceSet, restoredJavaProject.testSourceSet)
    assertEquals(newJavaProject.extraSourceSets, restoredJavaProject.extraSourceSets)
    assertEquals(newJavaProject.javaLanguageLevel, restoredJavaProject.javaLanguageLevel)

    assertEquals(newJavaProject, restoredJavaProject)
    assertEquals(javaProjectProto, restoredJavaProto)
  }
}