/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.common.model.NlDependencyManager
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.intellij.testFramework.registerExtension
import junit.framework.TestCase

open class NlDependencyManagerTest : LayoutTestCase() {
  private lateinit var projectSystem: TestProjectSystem
  private lateinit var model: NlModel
  private lateinit var nlDependencyManager: NlDependencyManager

  override fun setUp() {
    super.setUp()
    projectSystem = TestProjectSystem(project, availableDependencies = PLATFORM_SUPPORT_LIBS + NON_PLATFORM_SUPPORT_LAYOUT_LIBS)
    project.registerExtension<AndroidProjectSystemProvider>(EP_NAME, projectSystem, testRootDisposable)
    nlDependencyManager = NlDependencyManager()
    model = model("model.xml",
                  component(SdkConstants.CONSTRAINT_LAYOUT.defaultName())
                    .withBounds(0, 0, 10, 10)
                    .children(
                      component(SdkConstants.CARD_VIEW.defaultName())
                        .withBounds(1, 1, 1, 1)
                    )).build()
  }

  fun testEnsureLibraryIsIncluded() {
    val depsShouldBeAdded = listOf(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+"),
                                   GoogleMavenArtifactId.CARDVIEW_V7.getCoordinate("+"))
    nlDependencyManager.addDependencies(model.components, model.facet)
    assertSameElements(projectSystem.getAddedDependencies(model.module), depsShouldBeAdded)
  }

  fun testIdentifiesMissingDependency() {
    TestCase.assertFalse(nlDependencyManager.isModuleDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myFacet))
  }

  fun testIdentifiesCorrectDependency() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myFacet.module, GradleVersion(1, 1))
    TestCase.assertTrue(nlDependencyManager.isModuleDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myFacet))
  }

  fun testGetModuleDependencyVersion() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myFacet.module, GradleVersion(1, 1))
    TestCase.assertEquals(nlDependencyManager.getModuleDependencyVersion(GoogleMavenArtifactId.APP_COMPAT_V7, model.facet),
                          GradleVersion(1, 1))
  }
}