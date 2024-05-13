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
package com.android.tools.idea.common.model

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.res.resolveResourceNamespace
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Predicate
import java.util.stream.Stream

@RequiresReadLock
fun findAttributeByPsi(element: PsiElement): ResourceReference? {
  assert(ApplicationManager.getApplication().isReadAccessAllowed)
  var nextElement = element
  while (nextElement != null) {
    if (nextElement is XmlAttribute) {
      val attribute = nextElement
      val namespace = attribute.resolveResourceNamespace(attribute.namespacePrefix) ?: return null
      return ResourceReference.attr(namespace, attribute.localName)
    }
    nextElement = nextElement.parent
  }
  return null
}

/** Reads information from tree structure of [NlComponent]. */
class NlTreeReader(private val file: () -> XmlFile) {
  private var nlRootComponent: NlComponent? = null

  val components: ImmutableList<NlComponent>
    get() = nlRootComponent?.let { ImmutableList.of(it) } ?: ImmutableList.of()

  fun setRootComponent(root: NlComponent?) {
    nlRootComponent = root
  }

  fun find(id: String): NlComponent? {
    return flattenComponents().filter { c: NlComponent? -> id == c!!.id }.findFirst().orElse(null)
  }

  fun find(condition: Predicate<NlComponent>): NlComponent? {
    return flattenComponents().filter(condition).findFirst().orElse(null)
  }

  fun flattenComponents(): Stream<NlComponent> {
    return if (nlRootComponent != null)
      Stream.of(nlRootComponent).flatMap { obj: NlComponent? -> obj!!.flatten() }
    else Stream.empty()
  }

  fun findViewByAccessibilityId(id: Long): NlComponent? {
    return nlRootComponent?.findViewByAccessibilityId(id)
  }

  fun findViewByTag(tag: XmlTag): NlComponent? {
    return nlRootComponent?.findViewByTag(tag)
  }

  fun findByOffset(offset: Int): ImmutableList<NlComponent> {
    val tag = PsiTreeUtil.findElementOfClassAtOffset(file(), offset, XmlTag::class.java, false)
    return if ((tag != null)) findViewsByTag(tag) else ImmutableList.of()
  }

  private fun findViewsByTag(tag: XmlTag): ImmutableList<NlComponent> {
    return nlRootComponent?.findViewsByTag(tag) ?: ImmutableList.of()
  }

  @RequiresReadLock
  fun findViewByPsi(element: PsiElement?): NlComponent? {
    assert(ApplicationManager.getApplication().isReadAccessAllowed)
    var nextElement = element
    while (nextElement != null) {
      if (nextElement is XmlTag) {
        return findViewByTag(nextElement)
      }
      // noinspection AssignmentToMethodParameter
      nextElement = nextElement.parent
    }

    return null
  }
}
