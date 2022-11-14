/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.util.ReflectionUtil
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper

/**
 * [Remapper] that renames all class references to certain packages and adds them the given prefix.
 * The names used are JVM internal names and thus separated with "/".
 */
private class RepackageRemapper(packagePrefixes: Collection<String>,
                                private val remappedPrefix: String) : Remapper() {
  private val packagePrefixes = packagePrefixes.toTypedArray()

  override fun map(internalName: String): String {
    for (element in packagePrefixes) {
      if (internalName.startsWith(element)) return "$remappedPrefix$internalName"
    }

    return internalName
  }
}

/**
 * Class providing re-maping for reflection `Class.forName` calls so they are redirected to the new name.
 *
 * All calls into [Class.forName] are replaced now into calls to this class and a new parameter is added with the new class that should be
 * called.
 * The `oldName` parameter is kept since it simplifies the rewriting of the call by only adding a new parameter at the end and avoiding to
 * rewrite the LOAD calls.
 *
 * This class needs to be visible since it is accessed from the user code.
 */
object ClassForNameHandler {
  @Suppress("UNUSED_PARAMETER") // oldName not used intentionally
  @JvmStatic
  fun forName(oldName: String, newName: String): Class<*>  {
    val caller = ReflectionUtil.getGrandCallerClass()!!
    return Class.forName(newName, true, caller.classLoader)
  }

  @Suppress("UNUSED_PARAMETER") // oldName not used intentionally
  @JvmStatic
  fun forName(module: Module, oldName: String, newName: String) {
    // This is not currently supported as Modules are not being used.
    throw UnsupportedOperationException()
  }

  @Suppress("UNUSED_PARAMETER") // oldName not used intentionally
  @JvmStatic
  fun forName(oldName: String, initialize: Boolean, classLoader: ClassLoader?, newName: String) =
    Class.forName(newName, initialize, classLoader)
}

/**
 * [ClassVisitor] that repackages certain classes with a new package name. This allows to have the same class in two separate
 * namespaces so it can
 */
class RepackageTransform(delegate: ClassVisitor,
                         packagePrefixes: Collection<String>,
                         remappedPrefix: String) :
  ClassRemapper(delegate,
                RepackageRemapper(packagePrefixes.map { it.fromPackageNameToBinaryName() },
                                  remappedPrefix.fromPackageNameToBinaryName())), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = RepackageTransform::class.qualifiedName + "," + com.google.common.hash.Hashing.goodFastHash(64)
    .newHasher()
    .putString(packagePrefixes.joinToString(","), Charsets.UTF_8)
    .putString(remappedPrefix, Charsets.UTF_8)
    .hash()
    .toString()

  /**
   * This re-mapper replaces all calls to [Class.forName] with calls to [ClassForNameHandler.forName] while adding an additional parameter that
   * correspond to the right name that should be called.
   *
   * In bytecode, the `forName` calls looks as:
   *
   * ```
   * LDC "com.android.tools.idea.rendering.classloading.TestClass"
   * INVOKESTATIC java/lang/Class.forName (Ljava/lang/String;)Ljava/lang/Class;
   * ```
   *
   * After applying the transform, the call will look as:
   * ```
   * LDC "com.android.tools.idea.rendering.classloading.TestClass"
   * LDC "internal.test.com.android.tools.idea.rendering.classloading.TestClass"
   * INVOKESTATIC internal/test/com/android/tools/idea/rendering/classloading/ClassForNameHandler.forName (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;
   * ```
   *
   * A second `LDC` is introduced to pass the remapped name that should be used instead. The original `java/lang/Class`, is transformed into a call
   * to [ClassForNameHandler].
   */
  private class ClassForNameRepackageMethodTransform(api: Int, delegate: MethodVisitor, private val remapper: Remapper): MethodVisitor(api, delegate) {
    private val repackageHandlerClassName = ClassForNameHandler::class.java.canonicalName.fromPackageNameToBinaryName()
    private var lastLoadedLdc: String? = null
    override fun visitLdcInsn(value: Any?) {
      // We save all LDC calls using strings. This is to save the LDC that happens before the [Class.forName] call and that will contain
      // the class name.
      super.visitLdcInsn(value)
      lastLoadedLdc = value as? String
    }

    /**
     * Takes the given [descriptor] and adds the new `String` parameter that will be used to pass the new name of the class.
     */
    private fun remapDescriptorForRepackageHandler(descriptor: String): String {
      val descriptorType = Type.getMethodType(descriptor)

      val newArguments =
        descriptorType.argumentTypes + arrayOf(Type.getType(String::class.java))
      return Type.getMethodDescriptor(descriptorType.returnType, *newArguments)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if (lastLoadedLdc != null && name == "forName" && owner == "java/lang/Class") {
        // If this is a call to forName, remap the old class name, and pass it as new parameter.
        // We also need to update the method descriptor to add the new parameter.
        super.visitLdcInsn(
          remapper.mapType(lastLoadedLdc!!.fromPackageNameToBinaryName()).fromBinaryNameToPackageName())
        super.visitMethodInsn(opcode,
                              repackageHandlerClassName,
                              name,
                              remapDescriptorForRepackageHandler(descriptor),
                              isInterface)
      }
      else
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

      lastLoadedLdc = null
    }
  }

  override fun visitMethod(access: Int,
                           name: String?,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor {
    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

    return ClassForNameRepackageMethodTransform(Opcodes.ASM9, methodVisitor, remapper)
  }
}