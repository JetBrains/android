/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedAccess
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedField
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedMethod
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationEnclosingMethod
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationInterface
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationModifiedField
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationRemovedAccess
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationRemovedField
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationRemovedMethod
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationSignature
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationSuperClass
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.LocalVariableDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.TryCatchBlockDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.EnclosingMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrInstructionList
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrTryCatchBlock
import com.android.utils.ILogger

// TODO: which annotations from Compose and Kotlin do we need to allow-list? Once we know, modifying other annotations should be an error.
class RegularClassVisitor(private val className: String, private val logger: ILogger) : ClassVisitor {
  private val changedMethods = mutableListOf<MethodDiff>()
  val modifiedMethods: List<MethodDiff> = changedMethods

  override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
    if (added.isNotEmpty()) {
      val msg = "added access flag(s): " + added.joinToString(", ")
      throw unsupportedSourceModificationAddedAccess(getlocation(), msg)
    }

    if (removed.isNotEmpty()) {
      val msg = "removed access flag(s): " + removed.joinToString(",")
      throw unsupportedSourceModificationRemovedAccess(getlocation(), msg)
    }
  }

  override fun visitSignature(old: String?, new: String?) {
    throw unsupportedSourceModificationSignature(getlocation(), "signature changed from '$old' to '$new'")
  }

  override fun visitSuperName(old: String?, new: String?) {
    throw unsupportedSourceModificationSuperClass(getlocation(), "superclass changed from '$old' to '$new'")
  }

  override fun visitInterfaces(added: Set<String>, removed: Set<String>) {
    throw unsupportedSourceModificationInterface(getlocation(), "interfaces changed; added '$added' and removed'$removed'")
  }

  override fun visitEnclosingMethod(old: EnclosingMethod?, new: EnclosingMethod?) {
    throw unsupportedSourceModificationEnclosingMethod(getlocation(), "enclosing method changed from '$old' to '$new'")
  }

  // Allow adding and removing synthetic methods, such as compiler-generated accessor methods.
  override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
    if (added.filterNot { it.isSynthetic() }.isNotEmpty()) {
      val msg = "added method(s): " + added.joinToString(", ") { it.getReadableDesc() }
      throw unsupportedSourceModificationAddedMethod(getlocation(), msg)
    }

    if (removed.filterNot { it.isSynthetic() }.isNotEmpty()) {
      val msg = "removed method(s): " + removed.joinToString(", ") { it.getReadableDesc() }
      throw unsupportedSourceModificationRemovedMethod(getlocation(), msg)
    }

    for (method in modified) {
      val visitor = RegularMethodVisitor(className, method.name, method.desc)
      method.accept(visitor)
      if (visitor.hasNonSourceInfoChanges) changedMethods.add(method)
    }
  }

  override fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {
    if (added.isNotEmpty()) {
      val msg = "added field(s): " + added.joinToString(", ") { it.name }
      throw unsupportedSourceModificationAddedField(getlocation(), msg)
    }

    if (removed.isNotEmpty()) {
      val msg = "removed field(s): " + removed.joinToString(", ") { it.name }
      throw unsupportedSourceModificationRemovedField(getlocation(), msg)
    }

    // TODO: if we want, we can traverse the field diff to build a more detailed message.
    if (modified.isNotEmpty()) {
      val msg = "modified field(s): " + modified.joinToString(", ") { it.name }
      throw unsupportedSourceModificationModifiedField(getlocation(), msg)
    }
  }

  private inline fun getlocation() = className.replace('/', '.')
}

private class RegularMethodVisitor(val className: String, val methodName: String, val methodDesc: String) : MethodVisitor {
  var hasNonSourceInfoChanges: Boolean = false
    private set

  override fun visitLocalVariables(added: List<IrLocalVariable>, removed: List<IrLocalVariable>, modified: List<LocalVariableDiff>) {
    hasNonSourceInfoChanges = true
  }

  override fun visitTryCatchBlocks(added: List<IrTryCatchBlock>, removed: List<IrTryCatchBlock>, modified: List<TryCatchBlockDiff>) {
    hasNonSourceInfoChanges = true
  }

  override fun visitInstructions(old: IrInstructionList, new: IrInstructionList) {
    // Filter out the debugging info that compose modifies on every change
    hasNonSourceInfoChanges = !onlyComposeDebugConstantChanges(old, new)
  }

  override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
    // handleUnsupportedChange("interfaces changed; added '$added' and removed'$removed'")
  }

  override fun visitSignature(old: String?, new: String?) {
    // handleUnsupportedChange("signature changed from '$old' to '$new'")
  }

  private fun getLocation() = "${className.replace('/', '.')}.$methodName$methodDesc"
}