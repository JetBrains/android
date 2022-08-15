/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import org.junit.Test

class GradlePropertiesModelTest : GradleFileModelTestCase() {
  @Test
  fun testGetPropertiesModel() {
    writeToBuildFile("")
    writeToPropertiesFile("foo=true")
    val pbm = projectBuildModel
    val propertiesModel = pbm.projectBuildModel?.propertiesModel!!
    val properties = propertiesModel.declaredProperties
    assertSize(1, properties)
    assertEquals("foo", properties[0].name)
    assertEquals(GradlePropertyModel.ValueType.STRING, properties[0].valueType)
    assertEquals("true", properties[0].getValue(STRING_TYPE))
  }

  @Test
  fun testNameWithDots() {
    writeToBuildFile("")
    writeToPropertiesFile("android.foo=true")
    val pbm = projectBuildModel
    val propertiesModel = pbm.projectBuildModel?.propertiesModel!!
    val properties = propertiesModel.declaredProperties
    assertSize(1, properties)
    assertEquals("android.foo", properties[0].name)
    assertEquals("android\\.foo", properties[0].fullyQualifiedName)
  }
}