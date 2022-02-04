/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX
import com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE
import com.android.tools.idea.psi.TagToClassMapper
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet

/**
 * Utility for looking up the [PsiClass] from an XML tag name.
 */
class NlPsiLookup(facet: AndroidFacet) {
  private val psiFacade = JavaPsiFacade.getInstance(facet.module.project)
  private val allScope = GlobalSearchScope.allScope(facet.module.project)
  private val tagMapper = TagToClassMapper.getInstance(facet.module)
  private val viewMap = tagMapper.getClassMap(CLASS_VIEW)
  private val preferenceMap = tagMapper.getClassMap(CLASS_PREFERENCE)
  private val appcompatPreferenceMap = tagMapper.getClassMap(CLASS_PREFERENCE_ANDROIDX.oldName())
  private val androidxPreferenceMap = tagMapper.getClassMap(CLASS_PREFERENCE_ANDROIDX.newName())

  fun classOf(tagName: String): PsiClass? {
    viewMap[tagName]?.let { return it }
    preferenceMap[tagName]?.let { return it }
    appcompatPreferenceMap[tagName]?.let { return it }
    androidxPreferenceMap[tagName]?.let { return it }
    return tagName.takeIf { it.contains('.') }?.let { psiFacade.findClass(tagName, allScope) }
  }
}
