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
package com.android.tools.idea.nav.safeargs.psi

import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet

/**
 * Light class for Args.Builder classes generated from navigation xml files.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 *  <action id="@+id/sendMessage" destination="@+id/editorFragment">
 *    <argument name="message" argType="string" />
 *  </action>
 * ```
 *
 * This would generate a builder class like the following:
 *
 * ```
 *  class EditorFragmentArgs {
 *    class Builder {
 *      Builder(String message);
 *      void setMessage(String message);
 *    }
 *    ...
 *  }
 * ```
 *
 * See also: [LightArgsClass], which own this builder.
 */
class LightArgsBuilderClass(facet: AndroidFacet, private val argsClass: LightArgsClass)
  : AndroidLightClassBase(PsiManager.getInstance(facet.module.project), setOf(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)) {
  companion object {
    const val BUILDER_NAME = "Builder"
  }

  private val name: String = BUILDER_NAME
  private val qualifiedName: String = "${argsClass.qualifiedName}.$BUILDER_NAME"

  override fun getName() = name
  override fun getQualifiedName() = qualifiedName
  override fun getContainingFile() = argsClass.containingFile
  override fun getContainingClass() = argsClass
  override fun getParent() = argsClass
  override fun isValid() = true
  override fun getNavigationElement() = argsClass.navigationElement
}