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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMember
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMemberName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Visitor
import com.android.tools.idea.lang.proguardR8.psi.resolveParentClasses
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType

/**
 *  Reports unresolved class members in Proguard/R8 files.
 */
class ProguardR8ClassMemberInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitClassMemberName(name: ProguardR8ClassMemberName) {
        super.visitClassMemberName(name)
        val reference = name.reference ?: return
        val classMember = reference.element.parentOfType<ProguardR8ClassMember>()!!
        if (classMember.resolveParentClasses().isNotEmpty() && reference.resolveReference().isEmpty()) {
          // We can't resolve reference and we highlight it with "unused" (gray colour)
          // because it's not an error in Proguard/R8 to specify class member that doesn't exist
          holder.registerProblem(name.reference!!, "The rule matches no class members", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
      }
    }
  }
}
