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

import android.databinding.tool.util.StringUtils
import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil
import com.android.utils.usLocaleCapitalize
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil

/**
 * PSI wrapper around class types that additionally expose information particularly useful in data binding expressions.
 *
 * Note: This class is adapted from [android.databinding.tool.reflection.ModelClass] from db-compiler.
 */
class PsiModelClass(val type: PsiType, val mode: DataBindingMode) {

  /**
   * An enum class that defines how a [PsiModelClass] exposes its members.
   */
  enum class MemberAccess {
    ALL_MEMBERS {
      override fun accept(psiModelMember: PsiModelMember) = true
    },
    /**
     * Access mode that only exposes statics.
     */
    STATICS_ONLY {
      override fun accept(psiModelMember: PsiModelMember) = psiModelMember.isStatic
    },
    /**
     * Access mode that only exposes non-statics.
     */
    NON_STATICS_ONLY {
      override fun accept(psiModelMember: PsiModelMember) = !psiModelMember.isStatic
    };

    abstract fun accept(psiModelMember: PsiModelMember): Boolean
  }

  /**
   * Constructs a [PsiClass] of the given [.type]. Returns null if [.type] is not an instance of [PsiClassType].
   */
  val psiClass: PsiClass?
    get() = (type as? PsiClassType)?.resolve()

  /**
   * Returns true if this ModelClass represents an array.
   */
  val isArray = type is PsiArrayType

  /**
   * Returns true if this is a Generic e.g. List&lt;String>.
   */
  val isGeneric = typeArguments.isNotEmpty()

  /**
   * Returns true if this is a wildcard type argument.
   */
  // b/129719057 implement wildcard
  private val isWildcard = false

  /**
   * Returns true if this ModelClass represents a void
   */
  private val isVoid = PsiTypes.voidType().equalsToText(type.canonicalText)

  /**
   * Returns true if this is a type variable. For example, in List&lt;T>, T is a type variable.
   * However, List&lt;String>, String is not a type variable.
   */
  // b/129719057 implement typeVar
  private val isTypeVar = false

  /**
   * Returns true if this ModelClass or its type arguments contains any type variable or wildcard.
   */
  // b/129719057 implement typeVar and wildCard so isIncomplete could return true
  val isIncomplete: Boolean
    get() = isTypeVar || isWildcard || typeArguments.any { typeArg -> typeArg.isIncomplete }

  /**
   * Returns a list of Generic type parameters for the class. For example, if the class
   * is List&lt;T>, then the return value will be a list containing T. null is returned
   * if this is not a generic type
   */
  private val typeArguments: List<PsiModelClass>
    get() = (type as? PsiClassType)?.parameters
              ?.map { typeParameter -> PsiModelClass(typeParameter, mode) }
            ?: listOf()

  /**
   * Returns the list of fields in the class and all its superclasses.
   */
  val allFields: List<PsiModelField>
    get() = (type as? PsiClassType)?.resolve()?.allFields?.map { PsiModelField(this, it) } ?: listOf()

  /**
   * Returns the list of methods in the class and all its superclasses.
   *
   * If a method is declared in multiple classes or interfaces, only the latest one are returned.
   * For example, toString() is declared in [java.lang.Object] and overridden in [java.lang.String],
   * only the overriding one is returned.
   */
  val allMethods: List<PsiModelMethod>
    get() {
      var psiClass = (type as? PsiClassType)?.resolve()

      // Usually, we do not need to take care of methods declared in interfaces because we only return their implementations.
      // However, when [psiClass] itself is an interface, we need to include all of them.
      // Example:
      // Interface A has method a().
      // Interface B has method b() and extends Interface A.
      // Abstract Class C implements interface B with method a(), b() and abstract method c().
      // Class D extends class C and overrides method c().
      // To get all methods from Class D, we need to go up to its super class and remove duplication (method c() from Class C).
      // Fortunately, there is no need to consider methods from Interface A and B because they are already implemented in Class C and D.
      // On the other hand, to get all methods from Interface B, we need to add methods from Interface A while removing duplication
      // is not needed.
      if (psiClass?.isInterface == true) {
        return psiClass.allMethods.map { PsiModelMethod(this, it) }
      }
      val methods = ArrayList<PsiModelMethod>()
      while (psiClass != null) {
        val newMethods = psiClass.methods.filter {
          // Only keep the methods that do not have equivalents in the result set with same name and signatures.
          newMethod ->
          methods.none { it.name == newMethod.name && MethodSignatureUtil.areOverrideEquivalent(it.psiMethod, newMethod) }
        }
        methods.addAll(newMethods.map { PsiModelMethod(this, it) })
        psiClass = psiClass.superClass
      }
      return methods
    }

  /**
   * Returns the [PsiSubstitutor] which can be used to resolve generic types for fields and methods.
   */
  val substitutor: PsiSubstitutor
    get() {
      // Create the substitutor for this class
      var substitutor = (type as? PsiClassType)?.resolveGenerics()?.substitutor ?: PsiSubstitutor.EMPTY
      // Add substitutors from its super types
      type.superTypes.forEach { superType ->
        substitutor = substitutor.putAll(PsiModelClass(superType, mode).substitutor)
      }
      return substitutor
    }

  /**
   * Returns true if this is an ObservableField, or any of the primitive versions
   * such as ObservableBoolean and ObservableInt
   */
  val isObservableField
    get() =
      psiClass?.let { resolvedClass ->
        mode.observableFields.any { className ->
          val observableFieldClass = PsiModelClass(LayoutBindingTypeUtil.parsePsiType(className, resolvedClass)!!, mode)
          observableFieldClass.isAssignableFrom(erasure())
        }
      } ?: false

  /**
   * Returns true if this is a LiveData
   */
  val isLiveData
    get() = psiClass?.let { resolvedClass ->
      val liveDataClass = PsiModelClass(LayoutBindingTypeUtil.parsePsiType(mode.liveData, resolvedClass)!!, mode)
      liveDataClass.isAssignableFrom(erasure())
    } ?: false

  /**
   * Returns true if this is a StateFlow
   */
  val isStateFlow
    get() = psiClass.takeIf { mode == DataBindingMode.ANDROIDX }?.let { resolvedClass ->
      val stateFlowClass = PsiModelClass(LayoutBindingTypeUtil.parsePsiType(SdkConstants.CLASS_STATE_FLOW, resolvedClass)!!, mode)
      stateFlowClass.isAssignableFrom(erasure())
    } ?: false

  /**
   * Returns the name of the simple getter method when this is an observable type or
   * `null` for any other type
   */
  private val observableGetterName: String?
    get() = when {
      isObservableField -> "get"
      isLiveData || isStateFlow -> "getValue"
      else -> null
    }

  /**
   * Returns a type that this current type is wrapping. For example, if this type is a `LiveData&lt;String>`, then
   * return `String`. If this is not a type that wraps another type, then its own type is returned.
   *
   * This method can be useful, for example, to allow code completion to provide methods / fields for the
   * underlying type instead of the parent type itself.
   *
   * see [isLiveData], [isStateFlow], [isObservableField]
   */
  val unwrapped: PsiModelClass
    get() = observableGetterName?.let { name ->
      // Find the return type of getter function from LiveData/StateFlow/ObservableField
      val getterTypeModelClass = getMethod(name, listOf(), MemberAccess.NON_STATICS_ONLY, allowProtected = false)?.returnType
                                 ?: return this
      // Recursively unwrap the getter type
      PsiModelClass(getterTypeModelClass.type, mode).unwrapped
    } ?: this

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
  fun erasure() = PsiModelClass(TypeConversionUtil.erasure(type), mode)

  /**
   * Finds public methods that matches the given name exactly. These may be resolved into
   * listener methods during Expr.resolveListeners.
   */
  fun findMethods(name: String, memberAccess: MemberAccess): List<PsiModelMethod> {
    return allMethods.filter { method ->
      method.isPublic &&
      method.name == name &&
      memberAccess.accept(method)
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
   * @param memberAccess a filter that accepts certain types of members -- can be static
   * members, non-static members or all members.
   * @param allowProtected true if the method can be protected as well as public.
   * @param unwrapObservableFields true if the method should check for auto-unwrapping the
   * observable field.
   *
   * @return An array containing all public methods with the name `name` and taking
   * `args` parameters.
   */
  private fun getMethods(name: String,
                         args: List<PsiModelClass>,
                         memberAccess: MemberAccess,
                         allowProtected: Boolean): List<PsiModelMethod> {
    return allMethods.filter { method ->
      (method.isPublic || (allowProtected && method.isProtected))
      && memberAccess.accept(method)
      && name == method.name
      && method.acceptsArguments(args)
    }
  }

  /**
   * Returns the public method with the name and other constrains.
   *
   * @param name The method name to find
   * @param args The arguments that the method should accept
   * @param memberAccess a filter that accepts certain types of members -- can be static
   * members, non-static members or all members.
   * @param allowProtected true if the method can be protected as well as public.
   * @param unwrapObservableFields true if the method should check for auto-unwrapping the
   * observable field.
   */
  fun getMethod(name: String,
                args: List<PsiModelClass>,
                memberAccess: MemberAccess,
                allowProtected: Boolean
  ): PsiModelMethod? {
    val methods = getMethods(name, args, memberAccess, allowProtected)
    if (methods.isEmpty()) {
      return null
    }
    var bestMethod = methods[0]
    for (i in 1 until methods.size) {
      bestMethod = PsiModelMethod.betterMatchWithArguments(args, bestMethod, methods[i])
    }
    return bestMethod
  }

  private fun getField(name: String, memberAccess: MemberAccess): PsiModelField? {
    return allFields.firstOrNull { field ->
      (name == field.name || name == stripFieldName(field.name))
      && memberAccess.accept(field)
      && field.isPublic
    }
  }

  /**
   * Returns the getter method or field that the name refers to.
   * @param name The name of the field or the body of the method name -- can be getName() or isName().
   * @param memberAccess a filter that accepts certain types of members -- can be static
   * members or all members
   * @return the getter method or field that the name refers to or null if none can be found.
   */
  fun findGetterOrField(name: String, memberAccess: MemberAccess): PsiModelMember? {
    if ("length" == name && isArray) {
      // TODO b/129771951 implement length with Observable
      return null
    }
    val capitalized = StringUtils.capitalize(name)!!
    val methodNames = arrayOf("get$capitalized", "is$capitalized")
    val method = methodNames
      .flatMap { methodName -> getMethods(methodName, ArrayList(), MemberAccess.NON_STATICS_ONLY, allowProtected = false) }
      .firstOrNull { method -> method.returnType?.isVoid == false }
    // could not find a method. Look for a public field
    return method ?: getField(name, memberAccess)
  }

  fun findSetter(fieldName: String, parameterType: PsiModelClass): PsiModelMethod? {
    val setterName = "set${fieldName.usLocaleCapitalize()}"
    return allMethods.firstOrNull { method ->
      method.isPublic
      && MemberAccess.NON_STATICS_ONLY.accept(method)
      && setterName == method.name
      && method.parameterTypes.size == 1
      && method.parameterTypes[0] == parameterType
    }
  }

  override fun equals(other: Any?): Boolean {
    val otherClass = other as? PsiModelClass ?: return false
    return type == otherClass.type && mode == otherClass.mode
  }

  override fun hashCode() = type.hashCode().xor(mode.hashCode())

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
