/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.model

import com.android.tools.idea.databinding.DataBindingMode
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil

/**
 * PSI wrapper around psi methods that additionally expose information particularly useful in data binding expressions.
 *
 * Note: This class is adapted from [android.databinding.tool.reflection.ModelMethod] from db-compiler.
 */
class PsiModelMethod(val psiMethod: PsiMethod, val mode: DataBindingMode) {

  val declaringClass: PsiModelClass?
    get() = psiMethod.containingClass?.let { PsiModelClass(PsiTypesUtil.getClassType(it), mode) }

  val parameterTypes by lazy(LazyThreadSafetyMode.NONE) {
    psiMethod.parameterList.parameters.map { PsiModelClass(it.type, mode) }.toTypedArray()
  }

  val name: String
    get() = psiMethod.name

  val returnType: PsiModelClass?
    get() = psiMethod.returnType?.let { PsiModelClass(it, mode) }

  val isVoid = PsiType.VOID == psiMethod.returnType

  val isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC)

  val isProtected = psiMethod.hasModifierProperty(PsiModifier.PROTECTED)

  val isStatic = psiMethod.hasModifierProperty(PsiModifier.STATIC)

  val isAbstract = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)

  /**
   * Returns true if the final parameter is a varargs parameter.
   */
  val isVarArgs = psiMethod.isVarArgs

  private fun getParameter(index: Int, parameterTypes: Array<PsiModelClass>): PsiModelClass? {
    val normalParamCount = if (isVarArgs) parameterTypes.size - 1 else parameterTypes.size
    return if (index < normalParamCount) {
      parameterTypes[index]
    }
    else {
      parameterTypes[parameterTypes.size - 1].componentType
    }
  }

  private fun isBoxingConversion(class1: PsiModelClass, class2: PsiModelClass): Boolean {
    return if (class1.isPrimitive != class2.isPrimitive) {
      class1.box() == class2.box()
    }
    else {
      false
    }
  }

  private fun getImplicitConversionLevel(primitive: PsiModelClass?): Int {
    return when {
      primitive == null -> -1
      primitive.isByte -> 0
      primitive.isChar -> 1
      primitive.isShort -> 2
      primitive.isInt -> 3
      primitive.isLong -> 4
      primitive.isFloat -> 5
      primitive.isDouble -> 6
      else -> -1
    }
  }

  private fun compareParameter(arg: PsiModelClass, thisParameter: PsiModelClass,
                               thatParameter: PsiModelClass): Int {
    when {
      thatParameter == arg -> return 1
      thisParameter == arg -> return -1
      isBoxingConversion(thatParameter, arg) -> return 1
      isBoxingConversion(thisParameter, arg) -> // Boxing/unboxing is second best
        return -1
      else -> {
        val argConversionLevel = getImplicitConversionLevel(arg)
        if (argConversionLevel != -1) {
          val oldConversionLevel = getImplicitConversionLevel(thatParameter)
          val newConversionLevel = getImplicitConversionLevel(thisParameter)
          if (newConversionLevel != -1 && (oldConversionLevel == -1 || newConversionLevel < oldConversionLevel)) {
            return -1
          }
          else if (oldConversionLevel != -1) {
            return 1
          }
        }
        // Look for more exact match
        if (thatParameter.isAssignableFrom(thisParameter)) {
          return -1
        }
      }
    }
    return 0 // no difference
  }

  /**
   * Returns true if this method matches the args better than the other.
   */
  fun isBetterArgMatchThan(other: PsiModelMethod, args: List<PsiModelClass>): Boolean {
    val otherParameterTypes = other.parameterTypes
    for (i in args.indices) {
      val arg = args[i]
      val thisParameter = getParameter(i, parameterTypes) ?: continue
      val thatParameter = other.getParameter(i, otherParameterTypes) ?: continue
      if (thisParameter == thatParameter) {
        continue
      }
      val diff = compareParameter(arg, thisParameter, thatParameter)
      if (diff != 0) {
        return diff < 0
      }
    }
    return false
  }

  private fun isImplicitConversion(from: PsiModelClass?, to: PsiModelClass?): Boolean {
    if (from == null || to == null) {
      return false
    }
    if (from.isPrimitive && to.isPrimitive) {
      if (from.isBoolean || to.isBoolean || to.isChar) {
        return false
      }
      val fromConversionLevel = getImplicitConversionLevel(from)
      val toConversionLevel = getImplicitConversionLevel(to)
      return fromConversionLevel <= toConversionLevel
    }
    else {
      val unboxedFrom = from.unbox()
      val unboxedTo = to.unbox()
      if (from != unboxedFrom || to != unboxedTo) {
        return isImplicitConversion(unboxedFrom, unboxedTo)
      }
    }
    return false
  }

  /**
   * @param args The arguments to the method
   * @return Whether the arguments would be accepted as parameters to this method.
   */
  // b/129771951 revisit the case when unwrapObservableFields is true
  fun acceptsArguments(args: List<PsiModelClass>): Boolean {
    if (!isVarArgs && args.size != parameterTypes.size || isVarArgs && args.size < parameterTypes.size - 1) {
      return false // The wrong number of parameters
    }
    var parametersMatch = true
    var i = 0
    while (i < args.size && parametersMatch) {
      var parameterType = getParameter(i, parameterTypes)!!
      var arg = args[i]
      if (parameterType.isIncomplete) {
        parameterType = parameterType.erasure()
      }
      if (!parameterType.isAssignableFrom(arg) && !isImplicitConversion(arg, parameterType)) {
        parametersMatch = false
      }
      i++
    }
    return parametersMatch
  }
}
