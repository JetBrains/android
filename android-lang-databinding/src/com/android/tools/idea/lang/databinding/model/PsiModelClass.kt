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

import android.databinding.tool.BindableCompat
import android.databinding.tool.util.StringUtils
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import java.util.ArrayList

/**
 * PSI wrapper around class types that additionally expose information particularly useful in data binding expressions.
 *
 * Note: This class is adapted from [android.databinding.tool.reflection.ModelClass] from db-compiler.
 */
class PsiModelClass(val type: PsiType) {
  /**
   * Constructs a [PsiClass] of the given [.type]. Returns null if [.type] is not an instance of [PsiClassType].
   */
  val psiClass: PsiClass?
    get() = (type as? PsiClassType)?.resolve()

  /**
   * For arrays, lists, and maps, this returns the contained value. For other types, null
   * is returned.
   *
   * @return The component type for arrays, the value type for maps, and the element type
   * for lists.
   */
  val componentType: PsiModelClass?
    get() {
      // TODO: Support list and map type.
      // For list, it's the return type of the method get(int). For method, it's the second generic type.
      return (type as? PsiArrayType)?.let { PsiModelClass(it).componentType }
    }

  /**
   * Returns true if this ModelClass represents an array.
   */
  val isArray = type is PsiArrayType

  /**
   * Returns true if this ModelClass represents a primitive type.
   */
  val isPrimitive: Boolean
    get() {
      val canonicalText = type.getCanonicalText(false)
      val boxed = PsiTypesUtil.boxIfPossible(canonicalText)
      return boxed != canonicalText
    }

  /**
   * Returns true if this ModelClass represents an array.
   */
  val isBoolean = PsiType.BOOLEAN.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java char
   */
  val isChar = PsiType.CHAR.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java byte
   */
  val isByte = PsiType.BYTE.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java short
   */
  val isShort = PsiType.SHORT.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java int
   */
  val isInt = PsiType.INT.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java long
   */
  val isLong = PsiType.LONG.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java float
   */
  val isFloat = PsiType.FLOAT.equalsToText(type.canonicalText)

  /**
   * Returns true if this ModelClass represents a Java double
   */
  val isDouble = PsiType.DOUBLE.equalsToText(type.canonicalText)

  /**
   * Returns true if this is a wildcard type argument or not.
   */
  // b/129719057 implement wildcard
  val isWildcard = false

  /**
   * Returns true if this ModelClass represents a void
   */
  val isVoid = PsiType.VOID.equalsToText(type.canonicalText)

  /**
   * Returns true if this is a type variable. For example, in List&lt;T>, T is a type variable.
   * However, List&lt;String>, String is not a type variable.
   */
  // b/129719057 implement typeVar
  val isTypeVar = false

  /**
   * Returns true if this ModelClass is an interface
   */
  val isInterface: Boolean
    get() = (type as? PsiClassType)?.resolve()?.isInterface ?: false

  /**
   * Returns true if this ModelClass or its type arguments contains any type variable or wildcard.
   */
  // b/129719057 implement typeVar and wildCard so isIncomplete could return true
  val isIncomplete: Boolean
    get()  = isTypeVar || isWildcard || typeArguments.any{ typeArg -> typeArg.isIncomplete}

  /**
   * Returns a list of Generic type parameters for the class. For example, if the class
   * is List&lt;T>, then the return value will be a list containing T. null is returned
   * if this is not a generic type
   */
  val typeArguments: List<PsiModelClass>
    get() = (type as? PsiClassType)?.parameters
              ?.map { typeParameter -> PsiModelClass(typeParameter) }
            ?: listOf()

  /**
   * Returns the list of fields in the class and all its superclasses.
   */
  val allFields: List<PsiModelField>
    get() = (type as? PsiClassType)?.resolve()?.allFields?.map { PsiModelField(it) } ?: listOf()

  /**
   * Returns the list of methods in the class and all its superclasses.
   */
  val allMethods: List<PsiModelMethod>
    get() = (type as? PsiClassType)?.resolve()?.allMethods?.map { PsiModelMethod(it) } ?: listOf()

  fun toJavaCode() = type.canonicalText

  /**
   * When this is a boxed type, such as Integer, this will return the unboxed value,
   * such as int. If this is not a boxed type, this is returned.
   *
   * @return The unboxed type of the class that this ModelClass represents or this if it isn't a
   * boxed type.
   */
  fun unbox() = this

  /**
   * When this is a primitive type, such as boolean, this will return the boxed value,
   * such as Boolean. If this is not a primitive type, this is returned.
   *
   * @return The boxed type of the class that this ModelClass represents or this if it isn't a
   * primitive type.
   */
  fun box() = this

  /**
   * Returns whether or not the type associated with `that` can be assigned to
   * the type associated with this ModelClass. If this and that only require boxing or unboxing
   * then true is returned.
   *
   * @param that the ModelClass to compare.
   * @return true if `that` requires only boxing or if `that` is an
   * implementation of or subclass of `this`.
   */
  fun isAssignableFrom(that: PsiModelClass) = type.isAssignableFrom(that.type)

  /**
   * Returns this class type without any generic type arguments.
   */
  fun erasure() = this

  /**
   * Finds public methods that matches the given name exactly. These may be resolved into
   * listener methods during Expr.resolveListeners.
   */
  fun findMethods(name: String, staticOnly: Boolean): List<PsiModelMethod> {
    return allMethods.filter { method ->
      method.isPublic &&
      method.name == name &&
      (!staticOnly || method.isStatic)
    }
  }

  /**
   * Returns an array containing all public methods (or protected if allowProtected is true)
   * on the type represented by this ModelClass with the name `name` and can
   * take the passed-in types as arguments. This will also work if the arguments match
   * VarArgs parameter.
   *
   * @param name The name of the method to find.
   * @param args The types that the method should accept.
   * @param staticOnly Whether only static methods should be returned or both instance methods
   * and static methods are valid.
   * @param allowProtected true if the method can be protected as well as public.
   * @param unwrapObservableFields true if the method should check for auto-unwrapping the
   * observable field.
   *
   * @return An array containing all public methods with the name `name` and taking
   * `args` parameters.
   */
  private fun getMethods(name: String, args: List<PsiModelClass>, staticOnly: Boolean,
                         allowProtected: Boolean, unwrapObservableFields: Boolean): List<PsiModelMethod> {
    return allMethods.filter { method ->
      (method.isPublic || (allowProtected && method.isProtected))
      && (!staticOnly || method.isStatic)
      && name == method.name
      && method.acceptsArguments(args)
    }
  }

  /**
   * Returns the public method with the name `name` with the parameters that
   * best match args. `staticOnly` governs whether a static or instance method
   * will be returned. If no matching method was found, null is returned.
   *
   * @param name The method name to find
   * @param args The arguments that the method should accept
   * @param staticOnly true if the returned method must be static or false if it does not
   * matter.
   * @param allowProtected true if the method can be protected as well as public.
   * @param unwrapObservableFields true if the method should check for auto-unwrapping the
   * observable field.
   */
  fun getMethod(name: String,
                args: List<PsiModelClass>,
                staticOnly: Boolean,
                allowProtected: Boolean,
                unwrapObservableFields: Boolean = false
  ): PsiModelMethod? {
    val methods = getMethods(name = name,
                             args = args,
                             staticOnly = staticOnly,
                             allowProtected = allowProtected,
                             unwrapObservableFields = unwrapObservableFields)
    return if (methods.isEmpty()) null else methods.fold(methods[0]) {
      best, cur -> if (cur.isBetterArgMatchThan(best, args)) cur else best
    }
  }

  private fun findSetter(getter: PsiModelMethod, originalName: String): PsiModelMethod? {
    val capitalized = StringUtils.capitalize(originalName)
    val possibleNames = when {
      originalName == getter.name -> arrayOf(originalName, "set" + capitalized!!)
      getter.name.startsWith("is") -> arrayOf("set" + capitalized!!, "setIs$capitalized")
      else -> arrayOf("set" + capitalized!!)
    }
    return possibleNames
      .map { findMethods(it, getter.isStatic) }
      .flatten()
      .firstOrNull { method -> method.parameterTypes.size == 1 && method.parameterTypes[0] == getter.returnType && method.isStatic == getter.isStatic }
  }

  private fun getField(name: String, allowPrivate: Boolean, isStatic: Boolean): PsiModelField? {
    return allFields.firstOrNull { field ->
      (name == field.name || name == PsiModelClass.stripFieldName(field.name))
      && field.isStatic == isStatic
      && (allowPrivate || field.isPublic)
    }
  }

  /**
   * Returns the getter method or field that the name refers to.
   * @param name The name of the field or the body of the method name -- can be name(),
   * getName(), or isName().
   * @param staticOnly Whether this should look for static methods and fields or instance
   * versions
   * @return the getter method or field that the name refers to or null if none can be found.
   */
  fun findGetterOrField(name: String, staticOnly: Boolean): PsiCallable? {
    if ("length" == name && isArray) {
      // TODO b/129771951 implement length with Observable
      return null
    }
    val capitalized = StringUtils.capitalize(name)!!
    val methodNames = arrayOf("get" + capitalized, "is$capitalized", name)
    for (methodName in methodNames) {
      val methods = getMethods(methodName, ArrayList(), staticOnly, allowProtected = false, unwrapObservableFields = false)
      for (method in methods) {
        if (method.isPublic && (!staticOnly || method.isStatic) &&
            method.returnType?.isVoid != true) {
          var flags = PsiCallable.DYNAMIC
          if (method.isStatic) {
            flags = flags or PsiCallable.STATIC
          }

          val bindable: BindableCompat?
          // if method is not bindable, look for a backing field
          val backingField = getField(name, true, method.isStatic)
          if (backingField != null && backingField.isBindable) {
            flags = flags or PsiCallable.CAN_BE_INVALIDATED
            bindable = backingField.bindableAnnotation
          }
          else {
            bindable = null
          }
          val setterMethod = findSetter(method, name)
          val setterName = setterMethod?.name
          return PsiCallable(PsiCallable.Type.METHOD, methodName,
                             setterName, method.returnType, method.parameterTypes.size,
                             flags, method, bindable)
        }
      }
    }

    // could not find a method. Look for a public field
    val publicField =
      if (staticOnly) {
        getField(name, false, true)
      }
      else {
        getField(name, false, false) ?: getField(name, false, true)
      } ?: return null

    val fieldType = publicField.fieldType
    var flags = 0
    var setterFieldName: String? = name
    if (publicField.isStatic) {
      flags = flags or PsiCallable.STATIC
    }
    if (!publicField.isFinal) {
      setterFieldName = null
      flags = flags or PsiCallable.DYNAMIC
    }

    return PsiCallable(PsiCallable.Type.FIELD, name, setterFieldName, fieldType, 0, flags, null, publicField.bindableAnnotation)
  }


  companion object {

    /**
     * Converts the target field name to a consistent value, e.g. stripping "m_" or other common prefixes
     */
    private fun stripFieldName(fieldName: String): String {
      if (fieldName.length > 2) {
        val start = fieldName[2]
        if (fieldName.startsWith("m_") && Character.isJavaIdentifierStart(start)) {
          return Character.toLowerCase(start) + fieldName.substring(3)
        }
      }
      if (fieldName.length > 1) {
        val start = fieldName[1]
        val fieldIdentifier = fieldName[0]
        if (fieldIdentifier == '_' || (fieldIdentifier == 'm' && Character.isJavaIdentifierStart(start) &&
                                       !Character.isLowerCase(start))) {
          return Character.toLowerCase(start) + fieldName.substring(2)
        }
      }
      return fieldName
    }
  }
}
