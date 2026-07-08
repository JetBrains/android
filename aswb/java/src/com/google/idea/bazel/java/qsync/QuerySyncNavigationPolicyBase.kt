/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.bazel.java.qsync

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.util.MethodSignatureUtil

/** Common implementation methods that shared by navigation policies. */
interface QuerySyncNavigationPolicyBase : ClsCustomNavigationPolicy {

  override fun getNavigationElement(clsClass: ClsClassImpl): PsiClass? {
    val containingClass = clsClass.containingClass as? ClsClassImpl
    if (containingClass != null) {
      return getNavigationElement(containingClass)?.findInnerClassByName(clsClass.name, false)
    }

    val clsFileImpl = clsClass.containingFile as? ClsFileImpl ?: return null
    val navElement = getNavigationElement(clsFileImpl)
    return (navElement as? PsiClassOwner)?.classes?.find { it.name == clsClass.name }
  }

  override fun getNavigationElement(clsMethod: ClsMethodImpl): PsiElement? {
    val clsClass = clsMethod.containingClass as? ClsClassImpl ?: return null
    val srcClass = getNavigationElement(clsClass) ?: return null
    return srcClass.findMethodsByName(clsMethod.name, false).firstOrNull { MethodSignatureUtil.areParametersErasureEqual(it, clsMethod) }
  }

  override fun getNavigationElement(clsField: ClsFieldImpl): PsiElement? {
    val clsClass = clsField.containingClass as? ClsClassImpl ?: return null
    val srcClass = getNavigationElement(clsClass) ?: return null
    return srcClass.findFieldByName(clsField.name, false)
  }
}
