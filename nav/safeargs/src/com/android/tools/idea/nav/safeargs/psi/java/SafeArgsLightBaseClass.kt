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
package com.android.tools.idea.nav.safeargs.psi.java

import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet

/**
 * Common functionality for all safe args light classes.
 */
abstract class SafeArgsLightBaseClass(facet: AndroidFacet,
                                      modulePackage: String,
                                      private val suffix: String,
                                      private val navigationResource: ResourceItem,
                                      destination: NavDestinationData)
  : AndroidLightClassBase(PsiManager.getInstance(facet.module.project), setOf(PsiModifier.PUBLIC, PsiModifier.FINAL)) {

  private val name: String
  private val qualifiedName: String
  private val backingFile: PsiJavaFile
  protected val backingResourceFile by lazy { findResourceFile() }

  init {
    val fileFactory = PsiFileFactory.getInstance(project)

    qualifiedName = destination.name.let { name ->
      val nameWithoutDirections = if (!name.startsWith('.')) name else "$modulePackage$name"
      "$nameWithoutDirections$suffix"
    }
    name = qualifiedName.substringAfterLast('.')

    // Create a placeholder backing file to represent this light class
    backingFile = fileFactory.createFileFromText("${name}.java", JavaFileType.INSTANCE,
                                                 "// This class is generated on-the-fly by the IDE.") as PsiJavaFile
    backingFile.packageName = (qualifiedName.substringBeforeLast('.'))
  }

  override fun getName() = name
  override fun getQualifiedName() = qualifiedName
  override fun getContainingFile() = backingFile
  override fun getContainingClass(): PsiClass? = null
  override fun isValid() = true
  override fun getNavigationElement(): PsiElement {
    return backingResourceFile ?: return super.getNavigationElement()
  }

  private fun findResourceFile(): XmlFile? {
    val virtualFile = navigationResource.getSourceAsVirtualFile() ?: return null
    return PsiManager.getInstance(project).findFile(virtualFile) as XmlFile
  }
}