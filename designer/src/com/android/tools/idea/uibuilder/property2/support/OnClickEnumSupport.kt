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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property2.api.EnumSupport
import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.model.MergedManifest
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.android.dom.converters.OnClickConverter
import java.util.*

/**
 * [EnumSupport] for the "onClick" attribute.
 *
 * Find the activity class for this XML layout file and find all
 * methods matching the signature for the "onClick" attribute.
 */
class OnClickEnumSupport(val model: NlModel) : EnumSupport {

  override val values: List<EnumValue>
    get() {
      val module = model.facet.module
      val configuration = model.configuration
      var activityClassName = configuration.activity
      val facade = JavaPsiFacade.getInstance(module.project)
      val classes: Collection<PsiClass>
      if (activityClassName != null) {
        if (activityClassName.startsWith(".")) {
          val manifest = MergedManifest.get(module)
          val pkg = StringUtil.notNullize(manifest.`package`)
          activityClassName = pkg + activityClassName
        }
        val activityClass = facade.findClass(activityClassName, module.moduleScope)
        classes = if (activityClass != null) listOf(activityClass) else emptyList()
      } else {
        var scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
        val activityClass = facade.findClass(CLASS_ACTIVITY, scope)
        if (activityClass != null) {
          scope = GlobalSearchScope.moduleScope(module)
          classes = ClassInheritorsSearch.search(activityClass, scope, true).findAll()
        } else {
          classes = emptyList()
        }
      }
      val values = mutableListOf<EnumValue>()
      val found = HashSet<String>()
      for (psiClass in classes) {
        found.clear()
        val index = values.size
        for (method in psiClass.methods) {
          if (psiClass == method.containingClass &&
            OnClickConverter.CONVERTER_FOR_LAYOUT.checkSignature(method) &&
            found.add(method.name)) {
            values.add(EnumValue.indented(method.name))
          }
        }
        if (values.size > index) {
          values[index] = values[index].withHeader(psiClass.name ?: "class")
        }
      }
      return values
    }
}
