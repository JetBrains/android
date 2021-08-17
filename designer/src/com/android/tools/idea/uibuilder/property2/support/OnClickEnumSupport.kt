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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.tools.idea.common.model.NlModel
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.android.dom.converters.OnClickConverter
import org.jetbrains.android.dom.manifest.getPackageName

/**
 * [EnumSupport] for the "onClick" attribute.
 *
 * Find the activity class for this XML layout file and find all
 * methods matching the signature for the "onClick" attribute.
 */
class OnClickEnumSupport(val model: NlModel) : EnumSupport {

  override val values: List<EnumValue>
    get() {
      val app = ApplicationManager.getApplication()
      val classes = app.runReadAction<Collection<PsiClass>> { findActivityClasses() }
      val sortedClasses = classes.sortedBy { it.name }
      val values = mutableListOf<EnumValue>()
      for (psiClass in sortedClasses) {
        val methodNames = app.runReadAction<List<String>> { getMethodNames(psiClass) }
        if (methodNames.isNotEmpty()) {
          val className = app.runReadAction<String?> { psiClass.name }
          values.add(EnumValue.header(className ?: "class"))
          methodNames.sorted().forEach { values.add(EnumValue.indented(it)) }
        }
      }
      return values
    }

  private fun findActivityClasses(): Collection<PsiClass> {
    val module = model.facet.module
    val configuration = model.configuration
    var activityClassName = configuration.activity
    val facade = JavaPsiFacade.getInstance(module.project)
    val classes: Collection<PsiClass>
    if (activityClassName != null) {
      if (activityClassName.startsWith(".")) {
        val pkg = getPackageName(module)
        activityClassName = pkg + activityClassName
      }
      val activityClass = facade.findClass(activityClassName, module.moduleScope)
      classes = if (activityClass != null) listOf(activityClass) else emptyList()
    }
    else {
      var scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
      val activityClass = facade.findClass(CLASS_ACTIVITY, scope)
      if (activityClass != null) {
        scope = GlobalSearchScope.moduleScope(module)
        classes = ClassInheritorsSearch.search(activityClass, scope, true).findAll()
      }
      else {
        classes = emptyList()
      }
    }
    return classes
  }

  private fun getMethodNames(psiClass: PsiClass): List<String> =
    psiClass.methods
      .filter { psiClass == it.containingClass && OnClickConverter.CONVERTER_FOR_LAYOUT.checkSignature(it) }
      .map { it.name }
}
