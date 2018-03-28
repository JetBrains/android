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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.api.BuildModelNotification.NotificationType.*
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.notifications.PropertyPlacementNotification
import org.gradle.internal.impldep.org.junit.Test
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

class BuildNotificationTest : GradleFileModelTestCase() {
  @Test
  fun testIncompleteParsingNotification() {
    val text = """
               ext {
               prop1 = 1 + 2
               prop2 = 1 * 2
               prop2 = object.getValue() ** object.getOtherValue()
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val notifications = buildModel.notifications
    assertSize(1, notifications.entries)

    val firstNotification = notifications[buildModel.virtualFile.path]!![0]!!
    assertFalse(firstNotification.isCorrectionAvailable)
    assertThat(INCOMPLETE_PARSE, equalTo(firstNotification.type))

    val expected = "Found the following unknown element types while parsing: " +
        "GrAdditiveExpressionImpl, GrMultiplicativeExpressionImpl, GrPowerExpressionImpl"
    assertThat(firstNotification.toString(), equalTo(expected))
  }

  @Test
  fun testIncompleteParsingNotificationFromAppliedAndParentFiles() {
    val parentText = """
                     def variable = 3 * 3 * 3 * 3
                     """.trimIndent()
    val childText = """
                    android {
                      def var = 1
                      targetSdkVersion = 26 + var
                    }""".trimIndent()
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")

    val buildModel = subModuleGradleBuildModel
    val notifications = buildModel.notifications
    assertSize(2, notifications.entries)

    run {
      val parentNotifications = notifications[myBuildFile.path]!!
      assertSize(1, parentNotifications)
      val firstNotification = parentNotifications[0]!!
      assertFalse(firstNotification.isCorrectionAvailable)
      assertThat(INCOMPLETE_PARSE, equalTo(firstNotification.type))
      val expected = "Found the following unknown element types while parsing: GrMultiplicativeExpressionImpl, GrAdditiveExpressionImpl"
      assertThat(firstNotification.toString(), equalTo(expected))
    }

    run {
      val subModuleNotifications = notifications[buildModel.virtualFile.path]!!
      assertSize(1, subModuleNotifications)
      val firstNotification = subModuleNotifications[0]!!
      assertFalse(firstNotification.isCorrectionAvailable)
      assertThat(INCOMPLETE_PARSE, equalTo(firstNotification.type))
      val expected = "Found the following unknown element types while parsing: GrMultiplicativeExpressionImpl, GrAdditiveExpressionImpl"
      assertThat(firstNotification.toString(), equalTo(expected))
    }
  }

  @Test
  fun testPropertyPlacementNotification() {
    val text = """
               ext {
                 prop  = "${'$'}{greeting}"
                 prop1 = prop
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val propertyModel = extModel.findProperty("greeting")
    propertyModel.setValue(ReferenceTo("prop"))

    val notifications =  buildModel.notifications[myBuildFile.path]!!
    assertSize(1, notifications)
    assertTrue(notifications[0] is PropertyPlacementNotification)
  }

  @Test
  fun testNoPropertyPlacementNotification() {
    val text = """
               ext {
                 prop  = "${'$'}{greeting}"
                 prop1 = prop
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val propertyModel = extModel.findProperty("greeting")
    propertyModel.setValue(ReferenceTo("hello"))

    val notifications =  buildModel.notifications[myBuildFile.path]!!
    assertSize(0, notifications)
  }
}