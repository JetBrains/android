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
package com.android.tools.idea.actions

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.projectsystem.EP_NAME
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.PLATFORM_SUPPORT_LIBS
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.google.common.truth.Truth.assertThat
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.testFramework.registerExtension
import org.jetbrains.android.AndroidTestCase

class AndroidInferNullityActionTest : AndroidTestCase() {
  private lateinit var myProjectSystem: TestProjectSystem
  private lateinit var myNullityManager: NullableNotNullManager

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    myProjectSystem = TestProjectSystem(project, PLATFORM_SUPPORT_LIBS)
    project.registerExtension(EP_NAME, myProjectSystem, testRootDisposable)
    myFixture.addClass(
      """
import android.graphics.Color;

public class TestNullity {
    public Color myMethod() {
        Color color = null;
        return color;
    }

    public Color myMethod1() {
        Color color = new Color();
        return color;
    }
}
""".trimIndent())
    myNullityManager = NullableNotNullManager.getInstance(project)
  }

  fun testSupportLibAnnotations() {
    myProjectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_ANNOTATIONS, myModule, GradleVersion(1, 1))
    runInferNullityAction()
    assertThat(myNullityManager.defaultNullable).isEqualTo("android.support.annotation.Nullable")
    assertThat(myNullityManager.defaultNotNull).isEqualTo("android.support.annotation.NonNull")
  }

  fun testAndroidxAnnotations() {
    myProjectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS, myModule, GradleVersion(1, 1))
    runInferNullityAction()
    assertThat(myNullityManager.defaultNullable).isEqualTo("androidx.annotation.Nullable")
    assertThat(myNullityManager.defaultNotNull).isEqualTo("androidx.annotation.NonNull")
  }

  // TODO: Make this finish without error
  private fun runInferNullityAction() {
    try {
      val action = AndroidInferNullityAnnotationAction()
      val scope = AnalysisScope(project)
      action.getAdditionalActionSettings(project, null)
      action.analyze(project, scope)
    }
    catch (e: RuntimeException) {
      // Having set a JPS project without the annotations library, we're going to end up here.
      // But that is ok since currently the tests only check what the default annotations are, and don't rely on the analysis succeeding.
      assertThat(e.message).contains("JetBrains annotations")
    }
  }
}
