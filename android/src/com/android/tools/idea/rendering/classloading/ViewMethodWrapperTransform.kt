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

import com.android.ide.common.rendering.api.ILayoutLog
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type

private const val ORIGINAL_SUFFIX = "_Original"

/**
 * Find the ILayoutLog#error method
 */
private val ERROR_METHOD_DESCRIPTION: String? = try {
  Type.getMethodDescriptor(
    ILayoutLog::class.java.getMethod("error", String::class.java, String::class.java, Throwable::class.java, Any::class.java, Any::class.java))
}
catch (e: NoSuchMethodException) {
  assert(false)
  ""
}

/**
 * [ClassVisitor] that catches the exceptions on certain View methods like onLayout, onMeasure or onDraw so they do not stop the
 * rendering of the whole view.
 */
class ViewMethodWrapperTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = ViewMethodWrapperTransform::class.qualifiedName!!

  private var currentClassName: String? = null

  override fun visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String?,
                     superName: String,
                     interfaces: Array<String>) {
    currentClassName = name
    super.visit(version, access, name, signature, superName, interfaces)
  }

  /**
   * Creates a new method that calls an existing "name"_Original method and catches any exception the method might throw.
   * The exception is logged via the Bridge logger.
   *
   *
   * Only void return type methods are currently supported.
   */
  private fun wrapMethod(access: Int,
                         name: String,
                         desc: String,
                         signature: String?,
                         exceptions: Array<String>?) {
    assert(Type.getReturnType(desc) === Type.VOID_TYPE) { "Non void return methods are not supported" }
    val mw = super.visitMethod(access, name, desc, signature, exceptions)
    val tryStart = Label()
    val tryEnd = Label()
    val tryHandler = Label()
    mw.visitTryCatchBlock(tryStart, tryEnd, tryHandler, "java/lang/Throwable")
    //try{
    mw.visitLabel(tryStart)
    mw.visitVarInsn(Opcodes.ALOAD, 0) // this
    // push all the parameters
    val argumentTypes = Type.getMethodType(
      desc).argumentTypes
    var nLocals = 1
    for (argType in argumentTypes) {
      mw.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), nLocals++)
    }
    mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
                       name + ORIGINAL_SUFFIX, desc, false)
    mw.visitLabel(tryEnd)
    val exit = Label()
    mw.visitJumpInsn(Opcodes.GOTO, exit)
    //} catch(Throwable t) {
    mw.visitLabel(tryHandler)
    mw.visitFrame(Opcodes.F_SAME1, 0, null, 1, arrayOf<Any>("java/lang/Throwable"))
    val throwableIndex = nLocals++
    mw.visitVarInsn(Opcodes.ASTORE, throwableIndex)
    //  Bridge.getLog().warning()
    mw.visitMethodInsn(Opcodes.INVOKESTATIC, "com/android/layoutlib/bridge/Bridge", "getLog",
                       "()Lcom/android/ide/common/rendering/api/ILayoutLog;", false)
    mw.visitLdcInsn(ILayoutLog.TAG_BROKEN)
    mw.visitLdcInsn("$name error")
    mw.visitVarInsn(Opcodes.ALOAD, throwableIndex)
    mw.visitInsn(Opcodes.ACONST_NULL)
    mw.visitInsn(Opcodes.ACONST_NULL)
    mw.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/android/ide/common/rendering/api/ILayoutLog", "error",
                       ERROR_METHOD_DESCRIPTION,
                       true)
    if ("onMeasure" == name) { // For onMeasure we need to generate a call to setMeasureDimension to avoid an exception when no size is set
      mw.visitVarInsn(Opcodes.ALOAD, 0) // this
      mw.visitInsn(Opcodes.ICONST_0) // measuredWidth
      mw.visitInsn(Opcodes.ICONST_0) // measuredHeight
      mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName, "setMeasuredDimension", desc, false)
    }
    mw.visitLabel(exit)
    mw.visitFrame(Opcodes.F_SAME, 0, null, 0, null)
    mw.visitInsn(Opcodes.RETURN)
    mw.visitMaxs((argumentTypes.size + 1).coerceAtLeast(5), nLocals)
  }

  override fun visitMethod(access: Int,
                           name: String,
                           desc: String,
                           signature: String?,
                           exceptions: Array<String>?): MethodVisitor {
    if (("onLayout" == name && "(ZIIII)V" == desc ||
         "onMeasure" == name && "(II)V" == desc ||
         "onDraw" == name && "(Landroid/graphics/Canvas;)V" == desc ||
         "onFinishInflate" == name && "()V" == desc) &&
        access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) != 0 &&
        access and Opcodes.ACC_ABSTRACT == 0) {
      wrapMethod(access, name, desc, signature, exceptions)
      // Make the Original method private so that it does not end up calling the inherited method.
      val modifiedAccess = access and Opcodes.ACC_PUBLIC.inv() and Opcodes.ACC_PROTECTED.inv() or Opcodes.ACC_PRIVATE
      return super.visitMethod(modifiedAccess, name + ORIGINAL_SUFFIX, desc, signature, exceptions)
    }
    return super.visitMethod(access, name, desc, signature, exceptions)
  }
}