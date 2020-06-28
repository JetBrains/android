/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading

import org.jetbrains.annotations.TestOnly
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import java.util.WeakHashMap
import kotlin.reflect.jvm.javaMethod

/**
 * Data class for the key tracking constants. This key allows to find the initial constant
 * and query the new values.
 */
data class ConstantKey(val constantPath: String, val initialValue: Any)

/**
 * Interface to be implemented by providers that can remap a constant into a different one.
 * The [remapConstant] method will be called at every use of a certain constant.
 */
interface ConstantRemapper {
  /**
   * Adds a new constant to be replaced.
   *
   * @param classLoader [ClassLoader] associated to the class where the constants are being replaced. This allows to have the same class
   * loaded by multiple class loaders and have different constant replacements.
   * @param className the name of the class that will have this constant replaced.
   *  The className must be in is internal representation form, like `package.subpackage.ClassName$InnerClass`.
   * @param methodName the name of the method where this constant is being replaced. The same constant can potentially have different
   *  replacements in different methods.
   * @param initialValue the initial value constant.
   * @param newValue the new value to replace the [initialValue] with.
   */
  fun addConstant(classLoader: ClassLoader, className: String, methodName: String, initialValue: Any, newValue: Any)

  /**
   * Returns if there are any constant definitions for the given className and method.
   */
  fun hasMethodDefined(className: String, methodName: String): Boolean

  /**
   * Method called by the transformed class to obtain the new constant value.
   *
   * @param thisObject the class instance that is obtaining the new constant value.
   * @param methodName the name of the method where the constant is being loaded.
   * @param initialValue the initial value of the constant. Used to lookup the new value.
   */
  fun remapConstant(thisObject: Any, methodName: String, initialValue: Any?): Any?
}

/**
 * Default implementation of [ConstantRemapper].
 */
object DefaultConstantRemapper : ConstantRemapper {
  /**
   * Replaced constants indexed by [ClassLoader] and the initial value.
   */
  private val perClassLoaderConstantMap: WeakHashMap<ClassLoader, MutableMap<ConstantKey, Any>> = WeakHashMap()

  /** Used as a "bloom filter" to decide if we need to instrument a given class/method and for debugging. */
  private val allKeys: WeakHashMap<ConstantKey, Boolean> = WeakHashMap()

  override fun addConstant(classLoader: ClassLoader, className: String, methodName: String, initialValue: Any, newValue: Any) {
    val classLoaderMap = perClassLoaderConstantMap.computeIfAbsent(classLoader) { mutableMapOf() }
    val constantKey = ConstantKey("${className}.${methodName}", initialValue)
    allKeys[constantKey] = true
    classLoaderMap[constantKey] = newValue
  }

  override fun hasMethodDefined(className: String, methodName: String): Boolean {
    val constantPath = "${className}.${methodName}"
    return allKeys.keys.any { it.constantPath == constantPath }
  }

  @TestOnly
  fun allKeysToText(): String =
    allKeys.keys.joinToString("\n")

  override fun remapConstant(thisObject: Any, methodName: String, initialValue: Any?): Any? {
    if (initialValue == null) return null
    val classLoaderMap = perClassLoaderConstantMap[thisObject.javaClass.classLoader] ?: return initialValue
    val constantKey = ConstantKey("${Type.getInternalName(thisObject.javaClass)}.${methodName}", initialValue)
    return classLoaderMap.getOrDefault(constantKey, initialValue)
  }
}

/**
 * Manager that allows remapping the constants of a given [ClassLoader].
 */
object ConstantRemapperManager {
  private var remapper: ConstantRemapper = DefaultConstantRemapper

  @TestOnly
  fun setRemapper(remapper: ConstantRemapper) {
    this.remapper = remapper
  }

  @TestOnly
  fun restoreDefaultRemapper() {
    remapper = DefaultConstantRemapper
  }

  fun getConstantRemapper(): ConstantRemapper = remapper

  /**
   * Method used by the transformed classes to retrieve the value of a constant. This method
   * does not have direct uses in Studio but it will be used by the modified classes.
   */
  @JvmStatic
  fun remapAny(thisObject: Any, methodName: String, value: Any?): Any? =
    remapper.remapConstant(thisObject, methodName, value)
}

/**
 * Method visitor that transforms the method to remove constant loading instruction and replace them
 * with calls to [ConstantRemapperManager.remapAny].
 */
private class LiveLiteralsMethodVisitor(private val methodName: String, delegate: MethodVisitor)
  : InstructionAdapter(Opcodes.ASM7, delegate) {
  /**
   * Returns the correct remap method for a given value. The remap method takes the constant value in the user code
   * and runs it through an Autobox/check/Unbox cycle. The user code deals with primitives and not with boxed types.
   */
  private fun getRemapper(value: Any?): Pair<java.lang.reflect.Method, Boolean> =
    when (value) {
      is Int -> PrimitiveTypeRemapper::remapInt.javaMethod!! to true
      is Float -> PrimitiveTypeRemapper::remapFloat.javaMethod!! to true
      is Double -> PrimitiveTypeRemapper::remapDouble.javaMethod!! to true
      is Short -> PrimitiveTypeRemapper::remapShort.javaMethod!! to true
      is Long -> PrimitiveTypeRemapper::remapLong.javaMethod!! to true
      is Char -> PrimitiveTypeRemapper::remapChar.javaMethod!! to true
      else -> ConstantRemapperManager::remapAny.javaMethod!! to false
    }

  /**
   * Outputs the code to load the constant from the [ConstantRemapperManager].
   */
  private fun writeConstantLoadingCode(value: Any) {
    val (remapMethod, isPrimitive) = getRemapper(value)
    val remapMethodDescriptor = Type.getMethodDescriptor(remapMethod)

    // Load this as first parameter and the original constant as second
    visitVarInsn(Opcodes.ALOAD, 0);
    super.visitLdcInsn(methodName)
    super.visitLdcInsn(value)
    invokestatic(
      Type.getInternalName(remapMethod.declaringClass),
      remapMethod.name,
      remapMethodDescriptor, false)
    if (!isPrimitive) {
      checkcast(Type.getType(value::class.java))
    }
  }

  /**
   * Intercepts small int operations. Larger ints will be handled by [visitLdcInsn].
   */
  override fun visitIntInsn(opcode: Int, operand: Int) {
    when (opcode) {
      Opcodes.SIPUSH -> writeConstantLoadingCode(operand)
      else -> super.visitIntInsn(opcode, operand)
    }
  }

  /**
   * This method intercepts LDC calls. This will be called for values like Strings. For small ints, [visitIntInsn] is used instead.
   */
  override fun visitLdcInsn(value: Any?) {
    if (value == null) {
      super.visitLdcInsn(value)
      return
    }

    writeConstantLoadingCode(value)
  }
}

/**
 * Class transformation that rewrites methods using [LiveLiteralsMethodVisitor].
 *
 * @param delegate the delegate [ClassVisitor].
 * @param shouldRedefineMethod function that receives the class name and the method name. If it returns false, the constant instrumentation
 *  will not be applied to that particular method. This allows to avoid instrumenting code for which constants have not been defined.
 *  It can be combined with [ConstantRemapper.hasMethodDefined] to only instrument methods that have redefinitions if they are known ahead
 *  of time.
 */
class LiveLiteralsTransform(delegate: ClassVisitor, private val shouldRedefineMethod: (String, String) -> Boolean) : ClassVisitor(
  Opcodes.ASM7, delegate) {
  private var className = ""

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
    className = name
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor =
    if (shouldRedefineMethod(className, name)) {
      LiveLiteralsMethodVisitor(name, super.visitMethod(access, name, descriptor, signature, exceptions))
    }
    else {
      // No constant remapping needed
      super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}