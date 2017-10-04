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
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

open class NlDependencyManagerTest : LayoutTestCase() {

  private lateinit var model: NlModel
  private lateinit var dummyDependenciesManager: DummyDependenciesManager
  private lateinit var nlDependencyManager: NlDependencyManager

  override fun setUp() {
    super.setUp()
    dummyDependenciesManager = DummyDependenciesManager()
    nlDependencyManager = NlDependencyManager.get(dummyDependenciesManager)
    model = model("model.xml",
        component(SdkConstants.CONSTRAINT_LAYOUT)
            .withBounds(0, 0, 10, 10)
            .children(
                component(SdkConstants.CARD_VIEW)
                    .withBounds(1, 1, 1, 1)
            )).build()
  }

  fun testEnsureLibraryIsIncluded() {
    val dummyDependenciesManager = DummyDependenciesManager()
    val nlDependencyManager = NlDependencyManager.get(dummyDependenciesManager)
    nlDependencyManager.addDependencies(model.components, model.facet)
    assertSameElements(dummyDependenciesManager.dependencies,
        listOf(
            GradleCoordinate("com.android.support.constraint", "constraint-layout", "+"),
            GradleCoordinate("com.android.support", "cardview-v7", "+")))
  }

  private open class DummyDependenciesManager : NlDependencyManager.DependencyManager {

    val dependencies = mutableSetOf(GradleCoordinate("com.android.support.constraint", "constraint-layout", "+"))
    val newlyAddedDependencies = mutableListOf<GradleCoordinate>()

    override fun findMissingDependencies(module: Module, dependencies: Iterable<GradleCoordinate>): Iterable<GradleCoordinate> {
      return dependencies.subtract(this.dependencies).toList()
    }

    override fun addDependencies(module: Module, dependencies: Iterable<GradleCoordinate>): Boolean {
      newlyAddedDependencies.clear()
      newlyAddedDependencies.addAll(dependencies)
      this.dependencies.addAll(dependencies)
      return true
    }

    override fun dependsOn(artifact: String, facet: AndroidFacet) = true
    override fun getVersionOfDependency(artifact: String, facet: AndroidFacet): GradleVersion? = null
    override fun dependenciesAccepted(module: Module, missingDependencies: Iterable<GradleCoordinate>) = true
  }

}