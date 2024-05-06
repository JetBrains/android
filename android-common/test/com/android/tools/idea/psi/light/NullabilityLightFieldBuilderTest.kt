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
package com.android.tools.idea.psi.light

import com.android.tools.idea.psi.createNullabilityAnnotation
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.JavaPsiTestCase

class NullabilityLightFieldBuilderTest : JavaPsiTestCase() {
  fun testCanCreateLightFieldBuilderWithNullabilityAnnotations() {
    val manager = PsiManager.getInstance(project)
    val stringType = PsiType.getTypeByName("java.lange.String", project, GlobalSearchScope.everythingScope(project));

    val nameField = NullabilityLightFieldBuilder(manager, "name", stringType, true, PsiModifier.PUBLIC, PsiModifier.FINAL)
    val nicknameField = NullabilityLightFieldBuilder(manager, "nickname", stringType, false, PsiModifier.PUBLIC, PsiModifier.FINAL)

    assertThat(nameField.annotations.contains(project.createNullabilityAnnotation(true, null)))
    assertThat(nicknameField.annotations.contains(project.createNullabilityAnnotation(false, null)))

    // Make sure nullability annotations didn't overwrite field modifiers
    assertThat(nameField.modifierList.hasModifierProperty(PsiModifier.PUBLIC)).isTrue()
    assertThat(nameField.modifierList.hasModifierProperty(PsiModifier.FINAL)).isTrue()
    assertThat(nameField.modifierList.hasModifierProperty(PsiModifier.STATIC)).isFalse()
  }
}
