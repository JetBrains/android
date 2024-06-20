/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.JavaPsiTestCase

class NullabilityUtilsTest : JavaPsiTestCase() {
  fun testCanCreateNullabilityAnnotationFromProject() {
    val nullNotNullManager = NullableNotNullManager.getInstance(project)
    assertThat(nullNotNullManager.notNulls.map { "@$it" }).contains(project.createNullabilityAnnotation(true, null).text)
    assertThat(nullNotNullManager.nullables.map { "@$it" }).contains(project.createNullabilityAnnotation(false, null).text)
  }

  fun testCanAnnotateTypesWithNullabilityAnnotations() {
    val dummyType = PsiType.getTypeByName("dummy.test.ExampleType", project, GlobalSearchScope.everythingScope(project));
    val nonNullDummyType = project.annotateType(dummyType, true, null)
    val nullableDummyType = project.annotateType(dummyType, false, null)

    assertThat(nonNullDummyType.annotations.single().text).isEqualTo(project.createNullabilityAnnotation(true, null).text)
    assertThat(nullableDummyType.annotations.single().text).isEqualTo(project.createNullabilityAnnotation(false, null).text)
  }
}