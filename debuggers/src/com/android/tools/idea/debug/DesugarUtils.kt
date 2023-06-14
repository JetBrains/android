/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest

/**
 * Utility class to deal with desugaring.
 *
 *
 * Desugaring is a transformation process for Android applications to adapt new language features to prior versions of Android. One example
 * of desugaring is the support of non-abstract methods (default and static) in interface types. The transformation implies to synthesize
 * a companion class where the code of each non-abstract methods is moved into.
 *
 * TODO: Merge into [AndroidPositionManager]?
 */
class DesugarUtils(
  private val positionManager: PositionManager,
  private val debugProcess: DebugProcess,
) {
  /**
   * Returns a list of [ClassPrepareRequest] that also contains references to types synthesized by desugaring.
   *
   *
   * If the given requests list contains an interface type that requires desugaring, this method will add a prepare request that matches
   * any inner type of the interface. Indeed, desugaring may have synthesized an inner companion class that contains the given position.
   */
  fun addExtraPrepareRequestsIfNeeded(
    requestor: ClassPrepareRequestor,
    position: SourcePosition,
    requests: MutableList<ClassPrepareRequest>
  ): List<ClassPrepareRequest> {
    ReadAction.compute<Unit, RuntimeException> {
      val element = position.elementAt
      val classHolder = element.getInterfaceParent() ?: return@compute
      if (element.isAbstractMethod()) {
        return@compute
      }

      // Breakpoint in a non-abstract method in an interface. If desugaring is enabled, we should have a companion class with the
      // actual code.
      // The companion class should be an inner class of the interface. Let's get notified of any inner class that is loaded and
      // check if the class contains the position we're looking for.
      val classPattern = classHolder.qualifiedName + "$*"
      val trampolinePrepareRequestor = ClassPrepareRequestor { debuggerProcess, referenceType ->
        if (referenceType.hasLocationsForPosition(position)) {
          requestor.processClassPrepare(debuggerProcess, referenceType)
        }
      }
      val request = debugProcess.requestsManager.createClassPrepareRequest(trampolinePrepareRequestor, classPattern)
      if (request != null) {
        requests.add(request)
      }
    }
    return requests
  }

  /**
   * Returns a list of [ReferenceType] that also contains references to types synthesized by desugaring.
   *
   *
   * If the given types list contains an interface type that requires desugaring, this method will add to the returned list any inner type
   * that contains the given position in one of its methods.
   */
  fun addExtraClassesIfNeeded(
    position: SourcePosition,
    types: List<ReferenceType>,
  ): List<ReferenceType> {
    // Find all interface classes that may have a companion class.
    val candidatesForDesugaringCompanion = types.filter { type ->
      ReadAction.compute<Boolean, RuntimeException> {
        debugProcess.project.findClassInAllScope(type)?.canBeTransformedForDesugaring()
      }
    }
    if (candidatesForDesugaringCompanion.isEmpty()) {
      return types
    }

    // There is at least one interface that may have a companion class synthesized by desugaring.
    val allLoadedTypes = debugProcess.virtualMachineProxy.allClasses()
    val companions = allLoadedTypes.filter { loadedType ->
      candidatesForDesugaringCompanion.any { candidate ->
        loadedType.isCompanion(candidate.name(), position)
      }
    }


    return types + companions
  }

  private fun ReferenceType.isCompanion(className: String, position: SourcePosition) =
    startsWith("$className$") && containsPosition(position)

  private fun ReferenceType.containsPosition(position: SourcePosition) =
    positionManager.locationsOfLine(this, position).isNotEmpty()

  private fun ReferenceType.hasLocationsForPosition(position: SourcePosition) =
    runCatching { positionManager.locationsOfLine(this, position).isNotEmpty() }.getOrDefault(false)
}

private fun ReferenceType.startsWith(prefix: String) = name().startsWith(prefix)

private fun Project.findClassInAllScope(type: ReferenceType) =
  JavaPsiFacade.getInstance(this).findClass(type.name(), GlobalSearchScope.allScope(this))

private fun PsiClass.canBeTransformedForDesugaring() = isInterface && methods.any { it.body != null }
private fun PsiElement.getInterfaceParent(): PsiClass? {
  val parent = PsiTreeUtil.getParentOfType(this, PsiClass::class.java)
  return if (parent?.isInterface == true) parent else null
}

private fun PsiElement.isAbstractMethod() = PsiTreeUtil.getParentOfType(this, PsiMethod::class.java)?.body == null
