/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.experimental.callgraph

import com.google.common.collect.HashMultimap
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** A precomputed class and overriding method hierarchy. */
interface ClassHierarchy {

  fun directInheritorsOf(superClass: UClass): Sequence<UClass>

  fun allInheritorsOf(superClass: UClass): Sequence<UClass> = directInheritorsOf(superClass).flatMap { allInheritorsOf(it) + it }

  fun directOverridesOf(superMethod: UMethod): Sequence<UMethod>

  fun allOverridesOf(superMethod: UMethod): Sequence<UMethod> = directOverridesOf(superMethod).flatMap { allOverridesOf(it) + it }
}

class MutableClassHierarchy : ClassHierarchy {
  private val directInheritors = HashMultimap.create<UClass, UClass>()
  private val directOverrides = HashMultimap.create<UMethod, UMethod>()

  override fun directInheritorsOf(superClass: UClass) = directInheritors[superClass].asSequence()

  override fun directOverridesOf(superMethod: UMethod) = directOverrides[superMethod].asSequence()

  fun addClass(subClass: UClass) {
    subClass.supers
        .mapNotNull { it.navigationElement.toUElementOfType<UClass>() }
        .forEach { directInheritors.put(it, subClass) }
  }

  fun addMethod(subMethod: UMethod) {
    subMethod.findSuperMethods()
        .mapNotNull { it.navigationElement.toUElementOfType<UMethod>() }
        .forEach { directOverrides.put(it, subMethod) }
  }
}

class ClassHierarchyVisitor : AbstractUastVisitor() {
  private val mutableClassHierarchy = MutableClassHierarchy()
  val classHierarchy: ClassHierarchy get() = mutableClassHierarchy

  override fun visitClass(node: UClass): Boolean {
    mutableClassHierarchy.addClass(node)
    return super.visitClass(node)
  }

  override fun visitMethod(node: UMethod): Boolean {
    mutableClassHierarchy.addMethod(node)
    return super.visitMethod(node)
  }
}
