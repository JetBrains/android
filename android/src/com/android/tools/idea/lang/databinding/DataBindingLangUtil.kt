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
@file:JvmName("DataBindingLangUtil")

package com.android.tools.idea.lang.databinding

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.res.DataBindingInfo
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import org.jetbrains.android.facet.AndroidFacet

const val JAVA_LANG = "java.lang."

/**
 * Given a [PsiElement], return an associated [DataBindingInfo] for it.
 * This will return null if the IDEA module of the [PsiElement] cannot be found,
 * doesn't have an Android facet attached to it, or databinding is not enabled on it.
 */
fun getDataBindingInfo(element: PsiElement): DataBindingInfo? {
  var dataBindingInfo: DataBindingInfo? = null
  val module = ModuleUtilCore.findModuleForPsiElement(element)
  if (module != null) {
    val facet = AndroidFacet.getInstance(module)
    if (facet != null && DataBindingUtil.isDataBindingEnabled(facet)) {
      val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
      val topLevelFile = InjectedLanguageUtil.getTopLevelFile(element)
      if (topLevelFile != null) {
        var name = topLevelFile.name
        name = name.substring(0, name.lastIndexOf('.'))
        dataBindingInfo = moduleResources.getDataBindingInfoForLayout(name)
      }
    }
  }
  return dataBindingInfo
}