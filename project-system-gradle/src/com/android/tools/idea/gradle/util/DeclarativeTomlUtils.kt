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
package com.android.tools.idea.gradle.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTableHeader

// returns path as keys or table segments from root to a given TOML element
fun generateExistingPath(psiElement: TomlElement, includeCurrent: Boolean = true): List<String> {
  fun bubbleUp(element: PsiElement?, list: MutableList<String>){
    if(element == null || element is TomlFile) return
    when(element){
      is TomlKeyValue -> {
        element.key.segments.reversed().forEach { list.add(it.text) }
        bubbleUp(element.parent, list)
      }
      is TomlHeaderOwner -> {
        element.header.key?.segments?.reversed()?.forEach { list.add(it.text)}
        bubbleUp(element.parent, list)
      }
      is TomlKeySegment -> {
        var currElement: TomlKeySegment? = element
        do {
          list.add(currElement!!.text)
          currElement = currElement.prevSibling?.prevSibling as? TomlKeySegment
        } while (currElement != null)
        if(element.parent?.parent is TomlTableHeader)
          bubbleUp(element.findParentOfType<TomlHeaderOwner>(true)?.parent, list) // jump over TomlHeaderOwner parent
        else
          bubbleUp(element.findParentOfType<TomlKeyValue>(true)?.parent, list) // jump over TomlKeyValue parent
      }
      else ->  bubbleUp(element.parent, list)
    }
  }

  val result = mutableListOf<String>()
  bubbleUp(psiElement, result)

  return result.reversed().let {
    if (!includeCurrent && it.isNotEmpty()) it.dropLast(1) else it
  }
}