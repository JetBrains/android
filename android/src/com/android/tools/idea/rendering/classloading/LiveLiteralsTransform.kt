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

import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.Opcodes.ACC_STATIC
import java.util.WeakHashMap
import kotlin.reflect.jvm.javaMethod

private const val CLASSLOADING_PACKAGE = "com.android.tools.idea.rendering.classloading."

/**
 * Data class for the key tracking constants. This key allows to find the initial constant
 * and query the new values.
 */
data class ConstantKey(val reference: String, val initialValue: Any)

/**
 * Pattern to identify lambda calls, usually $1, $2, etc.
 */
private val LAMBDA_PATTERN = Regex("\\$\\d+")

/**
 * Normalizes the className to be used by the constant finder.
 */
@VisibleForTesting
fun normalizeClassName(className: String) =
  if (className.indexOf('$') == -1)
    className
  else
    LAMBDA_PATTERN.replace(className, ".<anonymous>").replace('$', '.')

/**
 * Interface to be implemented by providers that can remap a constant into a different one.
 * The [remapConstant] method will be called at every use of a certain constant.
 */
interface ConstantRemapper: ModificationTracker {
  /**
   * Adds a new constant to be replaced.
   *
   * @param classLoader [ClassLoader] associated to the class where the constants are being replaced. This allows to have the same class
   * loaded by multiple class loaders and have different constant replacements.
   * @param reference the full path, expressed as an [LiteralUsageReference] to the constant use.
   * @param initialValue the initial value constant.
   * @param newValue the new value to replace the [initialValue] with.
   */
  fun addConstant(classLoader: ClassLoader?, reference: LiteralUsageReference, initialValue: Any, newValue: Any)

  /**
   * Removes all the existing constants that are remapped.
   */
  fun clearConstants(classLoader: ClassLoader?)

  /**
   * Method called by the transformed class to obtain the new constant value.
   *
   * @param source the class instance that is obtaining the new constant value.
   * @param isStatic when false [source] will refer to the `this` of the caller. If true, [source] will be the [Class] of the
   * caller.
   * @param methodName the name of the method where the constant is being loaded.
   * @param initialValue the initial value of the constant. Used to lookup the new value.
   */
  fun remapConstant(source: Any?, isStatic: Boolean, methodName: String, initialValue: Any?): Any?
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

  /** Cache of all the initial values we've seen. This allows avoiding checking the cached if the constant was never there. */
  private val initialValueCache: MutableSet<String> = mutableSetOf()

  /** Modification tracker that is updated everytime a constant is added/removed. */
  private val modificationTracker = SimpleModificationTracker()

  override fun addConstant(classLoader: ClassLoader?, reference: LiteralUsageReference, initialValue: Any, newValue: Any) {
    val classLoaderMap = perClassLoaderConstantMap.computeIfAbsent(classLoader) { mutableMapOf() }
    initialValueCache.add(initialValue.toString())
    val constantKey = ConstantKey(reference.fqName.asString(), initialValue)
    if (allKeys.put(constantKey, true) == null) {
      // This is a new key, update modification count.
      modificationTracker.incModificationCount()
    }
    classLoaderMap[constantKey] = newValue
  }

  override fun clearConstants(classLoader: ClassLoader?) {
    perClassLoaderConstantMap[classLoader]?.let {
      if (it.isNotEmpty()) {
        modificationTracker.incModificationCount()
        it.clear()
      }
    }
  }

  @TestOnly
  fun allKeysToText(): String =
    allKeys.keys.joinToString("\n")

  override fun remapConstant(source: Any?, isStatic: Boolean, methodName: String, initialValue: Any?): Any? {
    if (initialValue == null || !initialValueCache.contains(initialValue.toString())) return initialValue
    val classLoader = if (isStatic) {
      // For static methods, the passed source is the Class<> object for the instance.
      (source as? Class<*>)?.classLoader
    } else {
      // For non static, the instance is passed, get the Class first and then the class loader.
      source?.javaClass?.classLoader
    }
    val classLoaderMap = perClassLoaderConstantMap[classLoader]
                         ?: perClassLoaderConstantMap[null] // fallback to the global constants
                         ?: return initialValue
    // Find the caller, by removing the remapConstant and currentThread frames and then looking for the
    // first element that does not belong to the classloading package.
    val callerStack = Thread.currentThread().stackTrace
      .drop(2)
      .dropWhile {
        it.className.startsWith(CLASSLOADING_PACKAGE)
      }.first()

    // Construct the lookupKey to find the constant in the constant map.
    // For lambdas, we ignore the invoke() method name in Kotlin.
    val lookupKey = if (methodName == "invoke")
      ConstantKey(normalizeClassName(callerStack.className), initialValue)
    else
      ConstantKey("${normalizeClassName(callerStack.className)}.${methodName}", initialValue)
    return classLoaderMap.getOrDefault(lookupKey, initialValue)
  }

  override fun getModificationCount(): Long = modificationTracker.modificationCount
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
  fun remapAny(source: Any?, isStatic: Boolean, methodName: String, value: Any?): Any? =
    remapper.remapConstant(source, isStatic, methodName, value)
}

/**
 * Method visitor that transforms the method to remove constant loading instruction and replace them
 * with calls to [ConstantRemapperManager.remapAny].
 */
private class LiveLiteralsMethodVisitor(access: Int, private val className: String, private val methodName: String, delegate: MethodVisitor)
  : InstructionAdapter(Opcodes.ASM7, delegate) {
  private val LOG = Logger.getInstance(LiveLiteralsMethodVisitor::class.java)
  private val isStaticMethod: Boolean = (access and ACC_STATIC) != 0

  /**
   * Track the bytecode line number. Since we care about user code, we only track elements in the code that have a line number.
   */
  private var lineNumber = -1

  override fun visitLineNumber(line: Int, start: Label?) {
    super.visitLineNumber(line, start)
    lineNumber = line
  }

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

    // Generate call to the static remapper method that loads the constant value
    if (!isStaticMethod) {
      // Load this as first parameter and the original constant as second
      visitVarInsn(Opcodes.ALOAD, 0)
    }
    else {
      // The "source" caller is a static method is the class itself
      super.visitLdcInsn(Type.getObjectType(className))
    }
    super.visitLdcInsn(isStaticMethod)
    super.visitLdcInsn(methodName)
    super.visitLdcInsn(value)
    invokestatic(
      Type.getInternalName(remapMethod.declaringClass),
      remapMethod.name,
      remapMethodDescriptor, false)
    if (!isPrimitive) {
      checkcast(Type.getType(value::class.java))
    }

    LOG.debug { "Instrumented constant access for $className.$methodName ($lineNumber) and original value '$value'" }
  }

  /**
   * Intercepts small int operations. Larger ints will be handled by [visitLdcInsn].
   */
  override fun visitIntInsn(opcode: Int, operand: Int) {
    if (lineNumber != -1) {
      when (opcode) {
        Opcodes.SIPUSH -> writeConstantLoadingCode(operand)
        Opcodes.BIPUSH -> writeConstantLoadingCode(operand)
        else -> super.visitIntInsn(opcode, operand)
      }
    }
    else {
      super.visitIntInsn(opcode, operand)
    }
  }

  /**
   * This method intercepts LDC calls. This will be called for values like Strings. For small ints, [visitIntInsn] is used instead.
   */
  override fun visitLdcInsn(value: Any?) {
    if (value == null || lineNumber == -1) {
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
      LiveLiteralsMethodVisitor(access, className, name, super.visitMethod(access, name, descriptor, signature, exceptions))
    }
    else {
      // No constant remapping needed
      super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}