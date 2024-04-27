/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.navigation

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.DECLARATIVE_TOML
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.util.generateExistingPath
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName
import org.toml.lang.psi.TomlKeySegment

class DeclarativeGoToApiDeclarationHandler: GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    val segment = (sourceElement?.parent as? TomlKeySegment) ?: return null

    val path = generateExistingPath(segment, false)
    val className = findDslElementClassName(path, segment.text) ?: return null

    val project = sourceElement.project
    val scope = GlobalSearchScope.allScope(project)
    val cl = JavaPsiFacade.getInstance(project).findClass(className, scope) ?: return null
    return if(path.isEmpty()) cl else findProperty(cl, segment.text)
  }
}

private fun findProperty(clazz: PsiClass, propertyName: String): PsiElement? {
  // sometimes setter has the same as property
  var methods = clazz.findMethodsByName(propertyName, true)
  if (methods.isEmpty()) {
    // if it does not work - try standard setter name
    methods = clazz.findMethodsByName("set" +
                                      StringUtils.capitalize(propertyName),
                                      true)
  }
  val method = methods.firstOrNull {
    it.containingClass?.isInterface == true &&
    it.containingClass?.qualifiedName?.startsWith("com.android.build.api.dsl") == true
  } ?: return null
  return if (method.textOffset == 0) {
    // if we have Kotlin property - need to find it in kotlinOrigin
    val ktOrigin = method.containingClass?.unwrapped
    val ktClass = (ktOrigin as? KtClass)
    ktClass?.findPropertyByName(propertyName)
  } else method
}

private fun findDslElementClassName(path: List<String>, propertyName: String): String? {
  var currentModel: GradlePropertiesDslElementSchema = GradleBuildFile.BuildGradlePropertiesDslElementSchema()
  if (path.isEmpty()) {
    // for first element (i.e. android or plugins) need to return android agp class
    val blockElement = currentModel.getBlockElementDescription(DECLARATIVE_TOML, propertyName) ?: return null
    currentModel = blockElement.schemaConstructor.construct()
  }
  else {
    path.forEach { element ->
      val blockElement = currentModel.getBlockElementDescription(DECLARATIVE_TOML, element) ?: return null
      currentModel = blockElement.schemaConstructor.construct()
    }
  }

  return currentModel.agpDocClass
}