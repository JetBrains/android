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
package com.android.tools.idea.testartifacts.testsuite.runconfiguration

import com.android.tools.idea.testing.AndroidProjectRule
import junit.framework.TestCase.assertTrue
import kotlin.test.assertEquals
import org.jdom.Element
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TestSuiteRunConfigurationTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  private lateinit var configuration: TestSuiteRunConfiguration

  @Before
  fun setUp() {
    val factory = TestSuiteRunConfigurationType().configurationFactories[0]
    configuration = TestSuiteRunConfiguration(rule.project, factory, "Test Suite Config")
  }

  @Test
  fun testGetAndSetState() {
    val engineIds = setOf("engine1", "engine2")
    configuration.setTestEngineIds(engineIds)
    assertEquals(engineIds, configuration.getTestEngineIds())

    val taskName = "myTestTask"
    configuration.setTaskName(taskName)
    assertEquals(taskName, configuration.getTaskName())
  }

  @Test
  fun testSerializationAndDeserialization() {
    val engineIds = setOf("junit5", "journeys-test-engine")
    configuration.setTestEngineIds(engineIds)
    val taskName = "myTestTask"
    configuration.setTaskName(taskName)
    configuration.setShowsResultsInAndroidTestMatrix(true)
    configuration.setIsDeployableToDevice(true)

    val element = Element("configuration")
    configuration.writeExternal(element)

    val newConfiguration = TestSuiteRunConfiguration(rule.project, configuration.factory!!, "New Config")
    newConfiguration.readExternal(element)

    assertEquals(engineIds, newConfiguration.getTestEngineIds())
    assertEquals(taskName, newConfiguration.getTaskName())
    assertTrue(newConfiguration.showsResultsInAndroidTestMatrix())
    assertTrue(newConfiguration.isDeployableToDevice())
  }
}