/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.editors.literals.internal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type

/** Default name for the annotation providing the file metadata by the compiler. */
const val FILE_INFO_ANNOTATION = "androidx.compose.runtime.internal.LiveLiteralFileInfo"
private const val FILENAME_ATTRIBUTE = "file"

/** Default name for the annotation providing the accessor metadata by the compiler. */
const val INFO_ANNOTATION = "androidx.compose.runtime.internal.LiveLiteralInfo"
private const val KEY_ATTRIBUTE = "key"
private const val OFFSET_ATTRIBUTE = "offset"

/**
 * Java does not support unsigned types, and kotlin introduced them as experimental
 * types in version 1.3. To be able to differentiate signed and unsigned types of
 * integers in the JVM, kotlin adds a special suffix for every reference to variables
 * of the following types: [UByte], [UInt], [ULong] and [UShort].
 * [unsignedTypesSuffixes] is a list with those suffixes.
 *
 * These four types are no longer experimental since kotlin 1.5, and the current value
 * of the suffixes were first introduced in v1.4.10
 * (github.com/JetBrains/kotlin/blob/v1.4.10/libraries/tools/binary-compatibility-validator/reference-public-api/kotlin-stdlib-runtime-merged.txt).
 *
 * These suffixes hopefully won't change, but if they do, then errors like b/204986515 will happen again
 *
 * More info:
 * - https://kotlinlang.org/docs/basic-types.html#unsigned-integers
 * - https://kotlinlang.org/docs/inline-classes.html#mangling
 */
private val unsignedTypesSuffixes = listOf("-w2LRezQ", "-pVg5ArA", "-s-VKNKU", "-Mh2AYeg")

/**
 * Visitor for the constructor of `$LiveLiterals` classes. This will record the literal initialization so we can know the initial values.
 *
 * @param callback callback when a new constant has been initialized. Called with the literal key and the value.
 */
private class LiveLiteralsConstructorVisitor(delegate: MethodVisitor, private val callback: (String, Any?) -> Unit) : MethodVisitor(
  Opcodes.ASM9, delegate) {
  /**
   * Last constant pushed into the stack.
   */
  private var lastLdcLoad: Any? = null

  override fun visitLdcInsn(value: Any?) {
    lastLdcLoad = value
    super.visitLdcInsn(value)
  }

  override fun visitIntInsn(opcode: Int, operand: Int) {
    lastLdcLoad = operand
    super.visitIntInsn(opcode, operand)
  }

  override fun visitInsn(opcode: Int) {
    when (opcode) {
      Opcodes.ACONST_NULL -> lastLdcLoad = null
      Opcodes.ICONST_M1 -> lastLdcLoad = -1
      Opcodes.ICONST_0 -> lastLdcLoad = 0
      Opcodes.ICONST_1 -> lastLdcLoad = 1
      Opcodes.ICONST_2 -> lastLdcLoad = 2
      Opcodes.ICONST_3 -> lastLdcLoad = 3
      Opcodes.ICONST_4 -> lastLdcLoad = 4
      Opcodes.ICONST_5 -> lastLdcLoad = 5
      Opcodes.LCONST_0 -> lastLdcLoad = 0L
      Opcodes.LCONST_1 -> lastLdcLoad = 1L
      Opcodes.DCONST_0 -> lastLdcLoad = 0.0
      Opcodes.DCONST_1 -> lastLdcLoad = 1.0
      Opcodes.FCONST_0 -> lastLdcLoad = 0f
      Opcodes.FCONST_1 -> lastLdcLoad = 1f
      Opcodes.FCONST_2 -> lastLdcLoad = 2f
    }
    super.visitInsn(opcode)
  }

  /**
   * Called when `PUT_STATIC' is executed in the VM. Now, we can look at the saved constant and we have all
   * the data available to call the [callback].
   */
  private fun visitPutStatic(name: String?) {
    val lastLdc = lastLdcLoad
    lastLdcLoad = null
    if (name != null && lastLdc != null) callback(name, lastLdc)
  }

  override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
    if (opcode == Opcodes.PUTSTATIC) visitPutStatic(name)
    super.visitFieldInsn(opcode, owner, name, descriptor)
  }

  override fun visitEnd() {
    super.visitEnd()
    lastLdcLoad = null
  }
}

/**
 * Saved method descriptor.
 * @see ClassVisitor.visitMethod
 */
data class MethodData(val access: Int,
                      val name: String,
                      val descriptor: String?,
                      val signature: String?,
                      @Suppress("ArrayInDataClass") val exceptions: Array<out String>?)

fun isLiveLiteralsClassName(className: String) = className.substringAfterLast('/').startsWith("LiveLiterals${'$'}")

/**
 * [AnnotationVisitor] that record the annotations and its parameters and calls the [callback] with the resulting values.
 * The [callback] first parameter contains the annotation name and the second a key/value map with the parameter name and value.
 */
private class RecordAnnotationVisitor(
  val name: String, private val callback: (String, Map<String, String>) -> Unit) : AnnotationVisitor(Opcodes.ASM9) {
  private val parameters = mutableMapOf<String, String>()

  override fun visit(name: String?, value: Any?) {
    if (name != null && value != null) {
      parameters[name] = value.toString()
    }
  }

  override fun visitEnd() {
    callback(name, parameters)
  }
}

/**
 * Transformation applied to LiveLiterals classes as generated by the Compose compiler.
 *
 * The Compose compiler, when live literals is enabled, will generate a companion class with the suffix `$LiveLiterals`
 * for every class. This class will encapsulate the access to the literals from the original class and can be used to override the behaviour
 * of the constants.
 * This transformation, will find all this `$LiveLiterals` classes and rewrite the accessor methods so the constants are read
 * from the [ConstantRemapper].
 *
 * The `$LiveLiterals` classes are also tagged with additional metadata that allow to map the request of a constant back to the place in the
 * source file where this was used:
 * - The `$LiveLiterals` class will have a top level [FILE_INFO_ANNOTATION] that will include the filename.
 * - Each accessor method will be also annotated with [INFO_ANNOTATION] with the offset of the literal in the source file.
 *
 * By using those two constants, we can trace back the accessor to the source file.
 */
abstract class LiveLiteralsFinder @JvmOverloads constructor(
  delegate: ClassVisitor?,
  private val fileInfoAnnotationName: String = FILE_INFO_ANNOTATION,
  private val infoAnnotationName: String = INFO_ANNOTATION) : ClassVisitor(
  Opcodes.ASM9, delegate) {
  private val LOG = Logger.getInstance(LiveLiteralsFinder::class.java)

  /**
   * The fully qualified class name.
   */
  protected var className = ""

  /**
   * True if the current class is a `$LiveLiterals` class.
   */
  private var liveLiteralsClass = false

  /**
   * Once the [FILE_INFO_ANNOTATION] has been processed, this will contain the filename of the source that offsets refer to.
   */
  private var sourceFileName: String? = null

  /**
   * Map containing the key name as emitted by the Compose compiler to the offset within the source code.
   */
  private val keyOffsets = mutableMapOf<String, Int>()

  /**
   * Map containing the key name as emitted by the Compose compiler to the initial value of the constant.
   */
  private val constantInitializationValues = mutableMapOf<String, Any?>()

  /**
   * All the accessor methods in the `$LiveLiterals` class are removed. This saves the descriptors so we can then
   * emit the rewritten versions.
   */
  private val methodsToRewrite = mutableListOf<MethodData>()

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
    className = name
    liveLiteralsClass = isLiveLiteralsClassName(className)
    super.visit(version, access, name, signature, superName, interfaces)
  }

  private fun visitLiveLiteralAnnotation(name: String, parameters: Map<String, String>) {
    LOG.debug("visitLiveLiteralAnnotation $name = $parameters")
    when (name) {
      fileInfoAnnotationName -> sourceFileName = parameters[FILENAME_ATTRIBUTE]
      infoAnnotationName -> keyOffsets[parameters[KEY_ATTRIBUTE] ?: error(
        "LiveLiteralInfo must define a key parameter")] = (parameters[OFFSET_ATTRIBUTE] ?: error(
        "LiveLiteralInfo must define an offset parameter")).toInt()
    }
  }

  /**
   * Called when a field is initialized.
   * @param key the key for the field as generated by the Compose compiler.
   * @param value the value to what the field is initialized to.
   */
  private fun visitFieldLoad(key: String, value: Any?) {
    LOG.debug("visitFieldLoad $key = $value")
    constantInitializationValues[key] = value
  }

  /**
   * Returns true if the given strings is one of the ones defining live literals compiler metadata.
   */
  private fun isLiveLiteralAnnotation(annotationName: String): Boolean =
    annotationName == infoAnnotationName || annotationName == fileInfoAnnotationName

  private fun visitAnnotation(descriptor: String?, delegate: AnnotationVisitor?): AnnotationVisitor? {
    val annotationName = Type.getType(descriptor).className
    return if (isLiveLiteralAnnotation(annotationName)) {
      RecordAnnotationVisitor(annotationName, this::visitLiveLiteralAnnotation)
    }
    else delegate
  }

  override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
    visitAnnotation(descriptor, super.visitAnnotation(descriptor, visible))

  override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor =
    if (liveLiteralsClass) {
      object : FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
          return this@LiveLiteralsFinder.visitAnnotation(descriptor, super.visitAnnotation(descriptor, visible))
        }
      }
    }
    else super.visitField(access, name, descriptor, signature, value)

  override fun visitMethod(access: Int,
                           name: String,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor {
    // We define this as a lambda to only invoke it when needed. This avoids generating
    // the visitCode output when we are removing the method.
    val delegate = {
      super.visitMethod(access, name, descriptor, signature, exceptions) ?: object : MethodVisitor(Opcodes.ASM9) {}
    }
    return if (liveLiteralsClass) {
      LOG.debug("visitMethod $name $descriptor")
      when (name) {
        "<clinit>" -> LiveLiteralsConstructorVisitor(delegate(), this::visitFieldLoad)
        "<init>" -> delegate()
        // Any other method gets removed
        else -> object : MethodVisitor(Opcodes.ASM9) {
          override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return this@LiveLiteralsFinder.visitAnnotation(descriptor, super.visitAnnotation(descriptor, visible))
          }

          override fun visitEnd() {
            methodsToRewrite.add(MethodData(access, name, descriptor, signature, exceptions))
          }
        }
      }
    }
    else delegate()
  }

  protected abstract fun onLiteralAccessor(fileName: String, offset: Int, initialValue: Any?, data: MethodData)

  override fun visitEnd() {
    // Rewrite all the methods that we have removed with the accessor code that reads the constant from the
    // ConstantRemapper.
    methodsToRewrite
      .forEach { data ->
        val name = removeUnsignedSuffix(data.name)
        LOG.debug { "Rewriting LiveLiterals method $name" }
        val fileName = sourceFileName
        val offset = keyOffsets[name]
        val initialValue = constantInitializationValues[name]
        requireNotNull(fileName) { "The file name must have been initialized by a '$FILE_INFO_ANNOTATION' annotation" }
        requireNotNull(offset) { "'${name}' key did not have an offset. Missing '$INFO_ANNOTATION'" }
        onLiteralAccessor(fileName, offset, initialValue, data)
      }

    super.visitEnd()
  }

  /**
   * See [unsignedTypesSuffixes].
   */
  private fun removeUnsignedSuffix(name: String): String {
    val unsignedTypeSuffix = unsignedTypesSuffixes.firstOrNull { name.endsWith(it) }
    return unsignedTypeSuffix?.let { name.removeSuffix(it) } ?: name
  }
}