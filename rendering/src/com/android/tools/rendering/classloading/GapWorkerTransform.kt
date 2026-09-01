/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * [ClassVisitor] that transforms `androidx.recyclerview.widget.GapWorker` and to make `add` and `postFromTraversal` no-op methods.
 *
 * In layoutlib / Studio preview rendering, gap prefetching is unnecessary because there is no continuous frame rendering loop. Furthermore,
 * when `RecyclerView.onDetachedFromWindow` is not called during session teardown, `GapWorker.sGapWorker` retains every instantiated
 * `RecyclerView` in its `mRecyclerViews` list, causing massive memory leaks.
 */
class GapWorkerTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  private var isGapWorkerClass: Boolean = false
  override val uniqueId: String = GapWorkerTransform::class.qualifiedName!!

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String, interfaces: Array<String>) {
    isGapWorkerClass = name == "androidx/recyclerview/widget/GapWorker"
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
    val mv = super.visitMethod(access, name, desc, signature, exceptions) ?: return null
    if (isGapWorkerClass && (name == "add" || name == "postFromTraversal")) {
      return NoOpMethodVisitor(mv)
    }
    return mv
  }

  private class NoOpMethodVisitor(private val myDelegate: MethodVisitor) : MethodVisitor(Opcodes.ASM9, null) {
    override fun visitCode() {
      myDelegate.visitInsn(Opcodes.RETURN)
      myDelegate.visitEnd()
    }
  }
}
