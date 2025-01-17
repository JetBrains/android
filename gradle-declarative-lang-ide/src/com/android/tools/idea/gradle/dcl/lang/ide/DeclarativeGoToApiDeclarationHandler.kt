/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.containers.tail
import org.apache.commons.lang3.StringUtils
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName

class DeclarativeGoToApiDeclarationHandler : GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    if (!DeclarativeIdeSupport.isEnabled()) return null
    val segment = (sourceElement?.parent as? DeclarativeIdentifier) ?: return null
    val segmentName = segment.name ?: return null

    val path = generateExistingPath(segment)
    val className = findDslElementClassName(path, segment) ?: return null

    val project = sourceElement.project
    val scope = GlobalSearchScope.allScope(project)
    val cl = JavaPsiFacade.getInstance(project).findClass(className, scope) ?: return null
    return if (path.isEmpty()) cl else findProperty(cl, segmentName)
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
  }
  else method
}

private fun findDslElementClassName(path: List<String>, element: DeclarativeIdentifier): String? {
  val schema = DeclarativeService.getInstance(element.project).getDeclarativeSchema() ?: return null
  val fileName = element.containingFile.name

  fun extractFqName(receivers: List<Entry>): String? =
    receivers.firstNotNullOf {
      when (it) {
        is SchemaFunction ->
          when (it.semantic) {
            is BlockFunction -> it.semantic.accessor.fqName.name
            is PlainFunction -> null
          }

        is DataProperty ->
          when (it.valueType) {
            is DataClassRef -> it.valueType.fqName.name
            is SimpleTypeRef -> null
          }
      }
    }

  if (path.isEmpty()) {
    return element.name?.let {
      val result = schema.getTopLevelEntriesByName(it, fileName)
      extractFqName(result)
    }
  }
  else {
    var receivers = schema.getTopLevelEntriesByName(path[0], fileName)
    for (i in 1 until path.size) {
      receivers = receivers.flatMap { it.getNextLevel (path[i]) }
    }
    return extractFqName(receivers)
  }
}

private fun generateExistingPath(start: DeclarativeIdentifier): List<String> {
  if (start.name == null) return listOf()
  val result = mutableListOf<String>()

  fun navigateToRoot(element: DeclarativeIdentifier?, action: (e: DeclarativeIdentifier) -> Unit) {
    if (element != null) {
      action(element)
      val block = element.parent.parentOfTypes(DeclarativeBlock::class)
      block?.identifier?.let {
        navigateToRoot(it, action)
      }
    }
  }

  navigateToRoot(start) { element ->
    element.name?.let { result.add(it) }
  }

  return result.tail().reversed()
}