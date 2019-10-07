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
package com.android.tools.idea.lang.proguardR8.psi

import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider


private val PROGUARD_R8_USAGE_TYPE = UsageType("Referenced in Proguard/R8 files")

/**
 * [UsageTypeProvider] that labels references from Proguard/R8 with the right description.
 *
 * @see PROGUARD_R8_USAGE_TYPE
 */
class ProguardR8UsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement?) = if (element?.containingFile is ProguardR8PsiFile) PROGUARD_R8_USAGE_TYPE else null
}
