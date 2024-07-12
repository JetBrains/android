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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.declarative.psi.DeclarativeElement
import com.android.tools.idea.gradle.declarative.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.declarative.psi.DeclarativeIdentifierOwner
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultFqName

class DeclarativeAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get()) return
    if (element.parent !is DeclarativeElement) return
    val parent = element.parent
    if (parent is DeclarativeIdentifier) {
      val path = getPath(parent)
      val service = DeclarativeService.getInstance(element.project)
      val schema = service.getSchema() ?: return
      if (schema.failureHappened) return
      verifyPath(path, schema, holder)
    }
  }

  private fun verifyPath(path: List<String>, schema: DeclarativeSchema, holder: AnnotationHolder) {
    if (path.isEmpty()) return
    var currentName: FqName? = null
    var parentData: DataClass? = null
    val last = path.size - 1
    for(index in 0 .. last) {
      currentName =
        if (index == 0) {
          getTopLevelReceiverByName(path[index], schema)
        }
        else {
          val dataClass = schema.getDataClassesByFqName()[currentName] ?: return

          val newName = getReceiverByName(path[index], dataClass.memberFunctions)
          if (index == last && newName == null) {
            // check last element in properties as well
            dataClass.memberFunctions.find { it.simpleName == path[index] } ?: dataClass.properties.find { it.name == path[index] }
            ?: if(!isNDOC(dataClass)) showUnknownName(holder) else Unit
            return
          }
          parentData = dataClass
          newName
        }

      if (currentName == null) {
        if (index == last && !isNDOC(parentData)) showUnknownName(holder)
        return
      }
    }
  }

  private fun isNDOC(parentDataClass:DataClass?) =
    parentDataClass?.supertypes?.contains(DefaultFqName("org.gradle.api", "NamedDomainObjectContainer")) == true


  private fun showUnknownName(holder: AnnotationHolder) {
    holder.newAnnotation(HighlightSeverity.ERROR,
                         "Unknown identifier").create()
  }

  private fun getPath(element: DeclarativeIdentifier): List<String> {
    val result = mutableListOf<String>()
    var current: PsiElement = element
    while (current.parent != null && current is DeclarativeElement) {
      if (current is DeclarativeIdentifierOwner) {
        current.identifier?.name?.let { result.add(it) }
      }
      current = current.parent
    }
    return result.reversed()
  }
}

