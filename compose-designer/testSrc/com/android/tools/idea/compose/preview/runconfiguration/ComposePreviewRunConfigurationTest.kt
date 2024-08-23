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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.activity.launch.EmptyTestConsoleView
import com.android.tools.idea.run.configuration.execution.createApp
import com.android.tools.idea.run.editor.NoApksProvider
import com.google.wireless.android.sdk.stats.ComposeDeployEvent
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class ComposePreviewRunConfigurationTest {

  private lateinit var runConfiguration: ComposePreviewRunConfiguration

  @get:Rule val projectRule = ComposeProjectRule()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    val runConfigurationFactory = ComposePreviewRunConfigurationType().configurationFactories[0]
    runConfiguration = ComposePreviewRunConfiguration(project, runConfigurationFactory)
  }

  @Test
  fun testAmStartOptionsWithComposableMethod() {
    runConfiguration.composableMethodFqn = "com.mycomposeapp.SomeClass.SomeComposable"
    runConfiguration.providerClassFqn = "com.mycomposeapp.ProviderClass"
    runConfiguration.providerIndex = 3

    val device = mock(IDevice::class.java)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.S_V2))
    val noApksProvider = NoApksProvider()
    runConfiguration.launch(
      createApp(
        device,
        "com.example.myapp",
        emptyList(),
        listOf("androidx.compose.ui.tooling.PreviewActivity"),
      ),
      device,
      AndroidFacet.getInstance(fixture.module)!!,
      "",
      false,
      noApksProvider,
      EmptyTestConsoleView(),
      RunStats(project),
    )
    verify(device)
      .executeShellCommand(
        eq(
          "am start -n com.example.myapp/androidx.compose.ui.tooling.PreviewActivity " +
            "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "--es composable com.mycomposeapp.SomeClass.SomeComposable" +
            " --es parameterProviderClassName com.mycomposeapp.ProviderClass" +
            " --ei parameterProviderIndex 3"
        ),
        any(),
        anyLong(),
        any(),
      )
  }

  @Test
  fun testTriggerSourceType() {
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.UNKNOWN_EVENT_TYPE,
      runConfiguration.triggerSource.eventType,
    )
    runConfiguration.triggerSource = ComposePreviewRunConfiguration.TriggerSource.GUTTER
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_GUTTER,
      runConfiguration.triggerSource.eventType,
    )
    runConfiguration.triggerSource = ComposePreviewRunConfiguration.TriggerSource.TOOLBAR
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_TOOLBAR,
      runConfiguration.triggerSource.eventType,
    )
  }

  @Test
  fun testReadExternal() {
    assertNull(runConfiguration.composableMethodFqn)

    val testConfig =
      // language=xml
      """
        <root>
          <compose-preview-run-configuration composable-fqn="com.example.MyClassKt.ExampleComposable"/>
        </root>
      """
        .trimIndent()

    runConfiguration.readExternal(JDOMUtil.load(testConfig))

    assertEquals("com.example.MyClassKt.ExampleComposable", runConfiguration.composableMethodFqn)
  }

  @Test
  fun testWriteExternal() {
    runConfiguration.composableMethodFqn = "com.example.MyClassKt.ExampleComposable"

    val testElement = Element("test")
    runConfiguration.writeExternal(testElement)

    val config = JDOMUtil.write(testElement)
    assertTrue(
      config.contains(
        "<compose-preview-run-configuration composable-fqn=\"com.example.MyClassKt.ExampleComposable\" />"
      )
    )
  }
}
