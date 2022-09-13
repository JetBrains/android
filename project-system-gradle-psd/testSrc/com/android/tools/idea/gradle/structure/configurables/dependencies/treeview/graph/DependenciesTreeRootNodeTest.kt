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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DependenciesTreeRootNodeTest {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testTreeStructure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val node = DependenciesTreeRootNode(project, PsUISettings())

      // Note: indentation matters!
      val expectedProjectStructure = """
      project
          jModuleK
          jModuleL
          mainModule
          nestedZ
          android.arch.core:common:1.1.1
          android.arch.core:runtime:1.1.1
          android.arch.lifecycle:common:1.1.1
          android.arch.lifecycle:livedata:1.1.1
          android.arch.lifecycle:livedata-core:1.1.1
          android.arch.lifecycle:runtime:1.1.1
          android.arch.lifecycle:viewmodel:1.1.1
          com.android.support:animated-vector-drawable:28.0.0
          com.android.support:appcompat-v7
          com.android.support:asynclayoutinflater:28.0.0
          com.android.support:collections:28.0.0
          com.android.support:coordinatorlayout:28.0.0
          com.android.support:cursoradapter:28.0.0
          com.android.support:customview:28.0.0
          com.android.support:documentfile:28.0.0
          com.android.support:drawerlayout:28.0.0
          com.android.support:interpolator:28.0.0
          com.android.support:loader:28.0.0
          com.android.support:localbroadcastmanager:28.0.0
          com.android.support:print:28.0.0
          com.android.support:slidingpanelayout:28.0.0
          com.android.support:support-annotations:28.0.0
          com.android.support:support-compat:28.0.0
          com.android.support:support-core-ui:28.0.0
          com.android.support:support-core-utils:28.0.0
          com.android.support:support-fragment:28.0.0
          com.android.support:support-vector-drawable:28.0.0
          com.android.support:swiperefreshlayout:28.0.0
          com.android.support:versionedparcelable:28.0.0
          com.android.support:viewpager:28.0.0
          com.example.jlib:lib3
              lib3:0.6
              lib3:0.9.1
              lib3:1.0
          com.example.jlib:lib4
              lib4:0.6
              lib4:0.9.1
              lib4:1.0
          com.example.libs:lib1
              lib1:0.+
              lib1:0.9.1
              lib1:1.0
          com.example.libs:lib2
              lib2:0.9.1
              lib2:1.0
          lib (..)
          libsam1-1.1.jar (../lib)
          libs
          libsfd
          libsfr
          libspd
          libspr
          otherlibsfd""".trimIndent()
      val treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
      // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
      Assert.assertThat(treeStructure.toString(), CoreMatchers.equalTo(expectedProjectStructure))
    }
  }
}