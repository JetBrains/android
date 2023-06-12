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
package org.jetbrains.android.facet

import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.isClassPackageNeeded
import com.android.tools.lint.checks.RESTRICT_TO_ANNOTATION
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.android.util.AndroidUtils

/**
 * Returns class by name. It can be either short name for library classes or FQCN.
 */
private fun findClassByName(facet: AndroidFacet, name: String, baseClassName: String): PsiClass? {
  val module = facet.module
  val project = module.project
  val baseClass = JavaPsiFacade.getInstance(project).findClass(baseClassName, module.moduleWithLibrariesScope) ?: return null

  return if (!name.contains(".")) {
    val classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, module.moduleWithLibrariesScope)
    classes.find {
      it.qualifiedName != null &&
      InheritanceUtil.isInheritorOrSelf(it, baseClass, true)
    }
  }
  else {
    val classes = JavaPsiFacade.getInstance(project).findClasses(name, module.getModuleSystem().getResolveScope(ScopeType.MAIN))
    classes.find { InheritanceUtil.isInheritorOrSelf(it, baseClass, true) }
  }
}

/**
 * Return class that CAN be used in XML by name.  It can be either short name for library classes or FQCN.
 */
fun findClassValidInXMLByName(facet: AndroidFacet, name: String, baseClassName: String): PsiClass? {
  val candidate = findClassByName(facet, name, baseClassName) ?: return null
  if (!candidate.isVisibleInXml()) return null
  if (candidate.name == name &&
      isClassPackageNeeded(candidate.qualifiedName!!, candidate, StudioAndroidModuleInfo.getInstance(facet).moduleMinApi, baseClassName)) {
    return null
  }
  return candidate
}

/**
 * Return view by name. It can be either short name for library classes or FQCN.
 */
fun findViewClassByName(facet: AndroidFacet, name: String) = findClassByName(facet, name, CLASS_VIEW)

/**
 * Return view that CAN be used in XML by name.
 */
fun findViewValidInXMLByName(facet: AndroidFacet, name: String) = findClassValidInXMLByName(facet, name, CLASS_VIEW)

/**
 * Returns all tags by which class can be used in XML.
 *
 * For classes that can't be used in XML (i.e abstract) returns empty array.
 */
fun getTagNamesByClass(c: PsiClass, apiLevel: Int, parentClassQualifiedName: String?): Array<String> {
  return runReadAction {
    val name = c.name
    if (name == null || !c.isVisibleInXml()) {
      return@runReadAction emptyArray()
    }
    val qualifiedName = c.qualifiedName ?: return@runReadAction arrayOf(name)
    if (isClassPackageNeeded(qualifiedName, c, apiLevel, parentClassQualifiedName)) {
      return@runReadAction arrayOf(qualifiedName)
    }
    arrayOf(name, qualifiedName)
  }
}

fun PsiClass.isVisibleInXml(): Boolean {
  val modifierList: PsiModifierList = modifierList ?: return false
  val isPublic = modifierList.hasModifierProperty(PsiModifier.PUBLIC)
  val isRestricted = modifierList.hasAnnotation(RESTRICT_TO_ANNOTATION.oldName()) ||
                     modifierList.hasAnnotation(RESTRICT_TO_ANNOTATION.newName())
  return isPublic && !isRestricted && !AndroidUtils.isAbstract(this)
}

interface TagFromClassDescriptor {
  val clazz: PsiClass?
  val isContainer: Boolean
}
