/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.menu

import com.android.SdkConstants
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentBackend
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(Parameterized::class)
class SearchItemHandlerTest(
  private val projectMinSdk: Int,
  private val dependencies: List<GoogleMavenArtifactId>,
  private val expectedNameSpace: String,
  private val expectedValue: String,
) {
  private val model = mock(NlModel::class.java)
  private val newChild = mock(NlComponent::class.java)
  private val backend = mock(NlComponentBackend::class.java)
  private val handler = SearchItemHandler()

  @Rule
  @JvmField
  val rule =
    AndroidProjectRule.withAndroidModel(AndroidProjectBuilder().withMinSdk { projectMinSdk })

  companion object {
    @Parameterized.Parameters(
      name = "projectMinSdk={0}, dependencies={1}, expectedNameSpace={2}, expectedValue={3}"
    )
    @JvmStatic
    fun params(): List<Any> =
      listOf(
        arrayOf(
          10,
          listOf<Any>(),
          SdkConstants.ANDROID_URI,
          "android.support.v7.widget.SearchView",
        ),
        arrayOf(
          10,
          listOf<Any>(GoogleMavenArtifactId.APP_COMPAT_V7),
          SdkConstants.AUTO_URI,
          "android.support.v7.widget.SearchView",
        ),
        arrayOf(
          10,
          listOf<Any>(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7),
          SdkConstants.AUTO_URI,
          "android.support.v7.widget.SearchView",
        ),
        arrayOf(
          10,
          listOf<Any>(
            GoogleMavenArtifactId.APP_COMPAT_V7,
            GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7,
          ),
          SdkConstants.AUTO_URI,
          "android.support.v7.widget.SearchView",
        ),
        arrayOf(11, listOf<Any>(), SdkConstants.ANDROID_URI, "android.widget.SearchView"),
        arrayOf(
          11,
          listOf<Any>(GoogleMavenArtifactId.APP_COMPAT_V7),
          SdkConstants.AUTO_URI,
          "android.widget.SearchView",
        ),
        arrayOf(
          11,
          listOf<Any>(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7),
          SdkConstants.AUTO_URI,
          "android.widget.SearchView",
        ),
        arrayOf(
          11,
          listOf<Any>(
            GoogleMavenArtifactId.APP_COMPAT_V7,
            GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7,
          ),
          SdkConstants.AUTO_URI,
          "android.widget.SearchView",
        ),
      )
  }

  @Before
  fun setUp() {
    whenever(model.module).thenReturn(rule.module)
    whenever(model.facet).thenReturn(AndroidFacet.getInstance(rule.module))
    whenever(model.project).thenReturn(rule.project)
    whenever(newChild.model).thenReturn(model)
    whenever(newChild.backend).thenReturn(backend)

    val projectSystem = TestProjectSystem(rule.project)
    if (dependencies.isNotEmpty()) {
      val moduleSystem = projectSystem.getModuleSystem(rule.module)
      for (dependency in dependencies) {
        moduleSystem.registerDependency(dependency.getCoordinate("+"))
      }
      runInEdtAndWait {
        ProjectSystemService.getInstance(rule.project).replaceProjectSystemForTests(projectSystem)
        projectSystem.useInTests()
      }
    }
  }

  @Test
  fun onCreateApiLevelIs10() {
    handler.onCreate(null, newChild, InsertType.CREATE)
    verify(newChild).setAttribute(expectedNameSpace, "actionViewClass", expectedValue)
  }

  @Test
  fun testNoExceptionWhenInsertingChild() {
    handler.onChildInserted(mock(NlComponent::class.java), newChild, mock(InsertType::class.java))
  }
}
