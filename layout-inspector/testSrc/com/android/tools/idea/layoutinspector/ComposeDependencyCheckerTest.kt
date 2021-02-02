/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ide.common.repository.GradleCoordinate
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy

@RunsInEdt
class ComposeDependencyCheckerTest {
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(true)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val moduleSystem: DefaultModuleSystem
    get() = projectRule.module.getModuleSystem() as DefaultModuleSystem

  private var lastNotification: StatusNotification? = null
  private val lastRequested = mutableListOf<GradleCoordinate>()

  @Before
  fun before() {
    projectRule.fixture.addFileToProject("/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
        <application />
      </manifest>
    """.trimIndent())
    val projectSystem = projectRule.project.getProjectSystem() as DefaultProjectSystem
    val moduleSystem = spy(DefaultModuleSystem(projectRule.module))
    val fakeResult = Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>(emptyList(), emptyList(), "")
    doAnswer { invocation ->
      lastRequested.clear()
      lastRequested.addAll(invocation.getArgument(0))
      fakeResult
    }.`when`(moduleSystem).analyzeDependencyCompatibility(anyList())
    projectSystem.setModuleSystem(moduleSystem.module, moduleSystem)
    moduleSystem.usesCompose = true
    InspectorBannerService.getInstance(projectRule.project).notificationListeners.add { lastNotification = it }
  }

  @Test
  fun testToolingAndReflectionLibraryPresent() {
    moduleSystem.registerDependency(GoogleMavenArtifactId.COMPOSE_RUNTIME.getCoordinate("1.0.0-alpha11"))
    moduleSystem.registerDependency(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("1.0.0-alpha11"))
    moduleSystem.registerDependency(GoogleMavenArtifactId.KOTLIN_REFLECT.getCoordinate("1.4.2"))
    val checker = ComposeDependencyChecker(projectRule.project)
    checker.performCheck(createClient("com.example"))
    assertThat(lastNotification).isNull()
  }

  @Test
  fun testMissingToolingLibrary() {
    moduleSystem.registerDependency(GoogleMavenArtifactId.COMPOSE_RUNTIME.getCoordinate("1.0.0-alpha11"))
    moduleSystem.registerDependency(GoogleMavenArtifactId.KOTLIN_REFLECT.getCoordinate("1.4.2"))
    val checker = ComposeDependencyChecker(projectRule.project)
    checker.performCheck(createClient("com.example"))
    assertThat(lastNotification).isNotNull()
    assertThat(lastNotification?.message).isEqualTo(
      "To fully support inspecting Compose layouts, " +
      "your app project should include the compose tooling library.")
    assertThat(lastNotification?.actions).hasSize(3)
    assertThat(lastNotification?.actions?.get(0)?.templateText).isEqualTo("Add to Project")
    assertThat(lastNotification?.actions?.get(1)?.templateText).isEqualTo("Learn More")
    assertThat(lastNotification?.actions?.get(2)?.templateText).isEqualTo("Dismiss")

    // check the library attempted to add to the module: from the "Add to Project" action
    lastNotification?.actions?.get(0)?.actionPerformed(mock())
    assertThat(lastRequested).containsExactly(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("1.0.0-alpha11"))
  }

  @Test
  fun testMissingReflectionLibrary() {
    moduleSystem.registerDependency(GoogleMavenArtifactId.COMPOSE_RUNTIME.getCoordinate("1.4.0-alpha11"))
    moduleSystem.registerDependency(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("1.0.0-alpha11"))
    moduleSystem.registerDependency(GoogleMavenArtifactId.KOTLIN_STDLIB.getCoordinate("1.4.3"))
    val checker = ComposeDependencyChecker(projectRule.project)
    checker.performCheck(createClient("com.example"))
    assertThat(lastNotification).isNotNull()
    assertThat(lastNotification?.message).isEqualTo(
      "To fully support inspecting Compose layouts, " +
      "your app project should include the Kotlin reflection library.")
    assertThat(lastNotification?.actions).hasSize(3)
    assertThat(lastNotification?.actions?.get(0)?.templateText).isEqualTo("Add to Project")
    assertThat(lastNotification?.actions?.get(1)?.templateText).isEqualTo("Learn More")
    assertThat(lastNotification?.actions?.get(2)?.templateText).isEqualTo("Dismiss")

    // check the library attempted to add to the module: from the "Add to Project" action
    lastNotification?.actions?.get(0)?.actionPerformed(mock())
    assertThat(lastRequested).containsExactly(GoogleMavenArtifactId.KOTLIN_REFLECT.getCoordinate("1.4.3"))
  }

  @Suppress("SameParameterValue")
  private fun createClient(processName: String): InspectorClient {
    val client: InspectorClient = mock()
    val process: ProcessDescriptor = mock()
    `when`(client.process).thenReturn(process)
    `when`(process.name).thenReturn(processName)
    return client
  }
}
