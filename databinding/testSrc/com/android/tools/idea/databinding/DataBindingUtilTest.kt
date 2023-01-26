/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.ANDROIDX_DATA_BINDING_LIB
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.DATA_BINDING_LIB
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class DataBindingUtilTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()

  // Project rule initialization must NOT happen on the EDT thread
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(edtRule)

  // Legal cast because project rule is initialized with onDisk
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT + "/databinding"
  }

  @Test
  fun getDataBindingMode() {
    val projectSystem = TestProjectSystem(projectRule.project)
    runInEdtAndWait {
      projectSystem.useInTests()
    }
    val facet = projectRule.module.androidFacet!!
    assertThat(DataBindingUtil.getDataBindingMode(facet)).isEqualTo(DataBindingMode.NONE)

    projectSystem.addDependency(DATA_BINDING_LIB, facet.module, GradleVersion(1, 1))
    projectSystem.emulateSync(SyncResult.SUCCESS)
    assertThat(DataBindingUtil.getDataBindingMode(facet)).isEqualTo(DataBindingMode.SUPPORT)

    projectSystem.addDependency(ANDROIDX_DATA_BINDING_LIB, facet.module, GradleVersion(1, 1))
    projectSystem.emulateSync(SyncResult.SUCCESS)
    assertThat(DataBindingUtil.getDataBindingMode(facet)).isEqualTo(DataBindingMode.ANDROIDX)
  }

  @Test
  @RunsInEdt
  fun methodPatternsMatchExpected() {
    fixture.addFileToProject(
      "src/p1/p2/ModelWithGettersSetters.java",
      // language=JAVA
      """
      package p1.p2;

      public final class ModelWithGettersSetters {

       // Valid getter and setter formats

       boolean getBoolValue() { return true; }
       int getIntValue() { return 123; }
       String getStringValue() { return "Not used"; }
       boolean isBoolValue() { return true; }
       void setBoolValue(boolean value) {}
       void setIntValue(int value) {}
       void setStringValue(String value) {}

       // Invalid getters

       void getVoidValue() {} // getter can't return void
       int isIntValue() { return 9000; } // "is" getter must return boolean
       int getIntValue(int arg1, int arg2) { return arg1 + arg2; } // getter should take 0 params
       int get456() { return 456; } // Part after "get" should be a valid java identifier
       boolean is789() { return false; } // Part after "is" should be a valid java identifier

       // Invalid setters

       void setBoolValue() {} // Setter should take a single parameter
       int setIntValue(int value) { return value; } // Setter should return void
       void setStringValue(String value, boolean isBold) {} // Setter should take a single parameter
       void set321() { } // Part after "set" should be a valid java identifier

       // Misc. functions that aren't setters or getters

       void logErrors() {}
       boolean update() { return true; }
       int length() { return 20; }

      }
      """.trimIndent())
    val modelClass = fixture.findClass("p1.p2.ModelWithGettersSetters")

    val boolGetters = modelClass.methods.filter { m -> DataBindingUtil.isBooleanGetter(m) }
    boolGetters.forEach { method -> assertThat(method.parameters.size).isEqualTo(0) }
    boolGetters.forEach { method -> assertThat(method.returnType).isEqualTo(PsiTypes.booleanType()) }
    assertThat(boolGetters.map { m -> m.name }).containsExactly("isBoolValue")

    val getters = modelClass.methods.filter { m -> DataBindingUtil.isGetter(m) }
    getters.forEach { method -> assertThat(method.parameters.size).isEqualTo(0) }
    getters.forEach { method -> assertThat(method.returnType).isNotEqualTo(PsiTypes.voidType()) }
    assertThat(getters.map { m -> m.name }).containsExactly("getBoolValue", "getIntValue", "getStringValue")

    val setters = modelClass.methods.filter { m -> DataBindingUtil.isSetter(m) }
    setters.forEach { method -> assertThat(method.parameters.size).isEqualTo(1) }
    setters.forEach { method -> assertThat(method.returnType).isEqualTo(PsiTypes.voidType()) }
    assertThat(setters.map { m -> m.name }).containsExactly("setBoolValue", "setIntValue", "setStringValue")
  }
}