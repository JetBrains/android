/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.lang

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.InferredAnnotationProvider
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiParameter
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.sdk.AndroidSdkType
import org.junit.Rule
import org.junit.Test

/** Tests [AndroidSdkInferredAnnotationProvider]. */
class AndroidSdkInferredAnnotationProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testStubAnnotationsMissingFromSdkSources() {
    val fixture = projectRule.fixture
    val project = projectRule.project

    // We'll use Java API `System.clearProperty(String)` to test annotations; the parameter is marked @RecentlyNonNull in android.jar
    // but is left unannotated in sources. We mock java/util/System.java here because we don't have the real SDK sources handy.
    val javaLangSystemSource = fixture.addFileToProject(
      "android-sdk-sources/java/lang/System.java",
      """
      package java.lang;
      class System {
        public static String clearProperty(String key) { return ""; }
      }
      """.trimIndent()
    )
    val sdkSourceDir = checkNotNull(javaLangSystemSource.virtualFile.parent.parent.parent)
    val androidSdk = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).single()
    val sdkModifier = androidSdk.sdkModificator
    sdkModifier.removeRoots(OrderRootType.SOURCES)
    sdkModifier.addRoot(sdkSourceDir, OrderRootType.SOURCES)
    runWriteAction(sdkModifier::commitChanges)

    // Find the parameter in android.jar.
    val clearPropertyMethod = fixture.findClass("java.lang.System").findMethodsByName("clearProperty", false).single()
    val compiledParam = clearPropertyMethod.parameterList.parameters.single()
    assertThat(compiledParam.containingFile.fileType).isEqualTo(JavaClassFileType.INSTANCE)
    assertThat(compiledParam.hasAnnotation(RECENTLY_NON_NULL)).isTrue() // Accurate as of API level 33.

    // Find the corresponding source element.
    val sourceParam = compiledParam.navigationElement
    check(sourceParam is PsiParameter)
    assertThat(sourceParam.containingFile.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(sourceParam.hasAnnotation(RECENTLY_NON_NULL)).isFalse()

    // Check that we infer @RecentlyNonNull for the source parameter (and leave the compiled parameter untouched).
    val allProviders = InferredAnnotationProvider.EP_NAME.getExtensionList(project)
    val ourProvider = allProviders.filterIsInstance<AndroidSdkInferredAnnotationProvider>().single()
    assertThat(ourProvider.findInferredAnnotation(sourceParam, RECENTLY_NON_NULL)).isNotNull()
    assertThat(ourProvider.findInferredAnnotations(sourceParam).map(PsiAnnotation::getQualifiedName)).containsExactly(RECENTLY_NON_NULL)
    assertThat(ourProvider.findInferredAnnotation(compiledParam, RECENTLY_NON_NULL)).isNull()
    assertThat(ourProvider.findInferredAnnotations(compiledParam).map(PsiAnnotation::getQualifiedName)).isEmpty()

    // Check that our inferred annotations affect InferredAnnotationsManager.
    val inferredAnnotationsManager = InferredAnnotationsManager.getInstance(project)
    assertThat(inferredAnnotationsManager.findInferredAnnotation(sourceParam, RECENTLY_NON_NULL)).isNotNull()
    assertThat(inferredAnnotationsManager.findInferredAnnotation(sourceParam, RECENTLY_NULLABLE)).isNull()

    // Check that our inferred annotations affect IntelliJ inspections.
    val testUsage = fixture.addFileToProject(
      "src/Test.java",
      """
      package com.example;
      class Test {
        void foo() {
          System.clearProperty(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
        }
      }
      """.trimIndent()
    )
    @Suppress("UnstableApiUsage")
    fixture.enableInspections(DataFlowInspection::class.java)
    fixture.openFileInEditor(testUsage.virtualFile)
    fixture.checkHighlighting()
  }
}

private const val RECENTLY_NON_NULL = "androidx.annotation.RecentlyNonNull"
private const val RECENTLY_NULLABLE = "androidx.annotation.RecentlyNullable"
