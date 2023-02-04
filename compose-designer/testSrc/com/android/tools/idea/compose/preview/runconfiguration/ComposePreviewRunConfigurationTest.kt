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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.editor.NoApksProvider
import com.android.tools.idea.run.tasks.ActivityLaunchTask
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.util.LaunchStatus
import com.google.wireless.android.sdk.stats.ComposeDeployEvent
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.mock

class ComposePreviewRunConfigurationTest : AndroidTestCase() {

  private lateinit var runConfiguration: TestComposePreviewRunConfiguration

  override fun setUp() {
    super.setUp()
    val runConfigurationFactory = ComposePreviewRunConfigurationType().configurationFactories[0]
    runConfiguration = TestComposePreviewRunConfiguration(project, runConfigurationFactory)
  }

  fun testAmStartOptionsWithComposableMethod() {
    runConfiguration.composableMethodFqn = "com.mycomposeapp.SomeClass.SomeComposable"
    runConfiguration.providerClassFqn = "com.mycomposeapp.ProviderClass"
    runConfiguration.providerIndex = 3

    val status = mock(LaunchStatus::class.java)
    val consolePrinter = mock(ConsolePrinter::class.java)
    val device = mock(IDevice::class.java)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.S_V2))
    val noApksProvider = NoApksProvider()
    val task =
      runConfiguration.getApplicationLaunchTask(
        FakeApplicationIdProvider(),
        myFacet,
        "",
        false,
        status,
        noApksProvider,
        consolePrinter,
        device
      ) as
        ActivityLaunchTask
    assertEquals(
      "am start -n \"com.example.myapp/androidx.compose.ui.tooling.PreviewActivity\" " +
        "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
        "--es composable com.mycomposeapp.SomeClass.SomeComposable" +
        " --es parameterProviderClassName com.mycomposeapp.ProviderClass" +
        " --ei parameterProviderIndex 3",
      task.getStartActivityCommand(mock(IDevice::class.java), mock(ConsolePrinter::class.java))
    )
  }

  fun testTriggerSourceType() {
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.UNKNOWN_EVENT_TYPE,
      runConfiguration.triggerSource.eventType
    )
    runConfiguration.triggerSource = ComposePreviewRunConfiguration.TriggerSource.GUTTER
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_GUTTER,
      runConfiguration.triggerSource.eventType
    )
    runConfiguration.triggerSource = ComposePreviewRunConfiguration.TriggerSource.TOOLBAR
    assertEquals(
      ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_TOOLBAR,
      runConfiguration.triggerSource.eventType
    )
  }

  fun testReadExternal() {
    assertNull(runConfiguration.composableMethodFqn)

    val testConfig =
    // language=xml
    """
        <root>
          <compose-preview-run-configuration composable-fqn="com.example.MyClassKt.ExampleComposable"/>
        </root>
      """.trimIndent()

    runConfiguration.readExternal(JDOMUtil.load(testConfig))

    assertEquals("com.example.MyClassKt.ExampleComposable", runConfiguration.composableMethodFqn)
  }

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

  private class FakeApplicationIdProvider : ApplicationIdProvider {
    override fun getPackageName() = "com.example.myapp"

    override fun getTestPackageName(): Nothing? {
      return null
    }
  }

  private class TestComposePreviewRunConfiguration(
    project: Project,
    factory: ConfigurationFactory
  ) : ComposePreviewRunConfiguration(project, factory) {

    // Relax visibility to call the super method (which has protected visibility) in this test
    public override fun getApplicationLaunchTask(
      applicationIdProvider: ApplicationIdProvider,
      facet: AndroidFacet,
      contributorsAmStartOptions: String,
      waitForDebugger: Boolean,
      launchStatus: LaunchStatus,
      apkProvider: ApkProvider,
      consolePrinter: ConsolePrinter,
      device: IDevice
    ): AppLaunchTask? {
      return super.getApplicationLaunchTask(
        applicationIdProvider,
        facet,
        contributorsAmStartOptions,
        waitForDebugger,
        launchStatus,
        apkProvider,
        consolePrinter,
        device
      )
    }
  }
}
