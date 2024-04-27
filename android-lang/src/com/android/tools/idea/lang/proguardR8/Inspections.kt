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

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ArrayType
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMember
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMemberName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Flag
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Visitor
import com.android.tools.idea.lang.proguardR8.psi.impl.ProguardR8RuleWithClassFilterImpl
import com.android.tools.idea.lang.proguardR8.psi.isParentClassKnown
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.TokenType
import com.intellij.psi.util.parentOfType

/**
 *  Reports unresolved class members in Proguard/R8 files.
 */
class ProguardR8ReferenceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitClassMemberName(name: ProguardR8ClassMemberName) {
        super.visitClassMemberName(name)
        val reference = name.reference ?: return
        val classMember = reference.element.parentOfType<ProguardR8ClassMember>()!!
        if (classMember.isParentClassKnown() && reference.multiResolve(false).isEmpty()) {
          // We can't resolve reference and we highlight it with "unused" (gray colour)
          // because it's not an error in Proguard/R8 to specify class member that doesn't exist
          holder.registerProblem(name, "The rule matches no class members")
        }
      }

      override fun visitQualifiedName(name: ProguardR8QualifiedName) {
        super.visitQualifiedName(name)

        // Class filters with a `-dontwarn` or `-dontnote` often don't exist.
        if (name.parent?.parent is ProguardR8RuleWithClassFilterImpl) return

        // Names with wildcards won't resolve.
        if (name.containsWildcards()) return

        if (name.resolveToPsiClass() == null) {
          holder.registerProblem(name, "Unresolved class name")
        }
      }
    }
  }
}

/**
 * Reports invalid separator between class and inner class in Proguard/R8 files.
 */
class ProguardR8InnerClassSeparatorInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitQualifiedName(name: ProguardR8QualifiedName) {
        super.visitQualifiedName(name)
        if (!name.containsWildcards() && name.resolveToPsiClass() == null) {
          val lastResolvedClass = name.references.lastOrNull { it.resolve() is PsiClass } ?: return
          val nextSymbol = name.text[lastResolvedClass.rangeInElement.endOffset]
          if (lastResolvedClass.rangeInElement.endOffset + 1 != name.textLength && nextSymbol != '$') {
            holder.registerProblem(name, "Inner classes should be separated by a dollar sign \"\$\"")
          }
        }
      }
    }
  }
}

class ProguardR8ArrayTypeInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitArrayType(o: ProguardR8ArrayType) {
        super.visitArrayType(o)
        if (o.node.findChildByType(TokenType.WHITE_SPACE) != null) {
          holder.registerProblem(o, "White space is not allowed in array annotation, use 'type[]'")
        }
        else if (o.parent.node.findChildByType(TokenType.WHITE_SPACE) != null) {
          holder.registerProblem(o.parent, "White space between type and array annotation is not allowed, use 'type[]'")
        }
      }
    }
  }
}

/**
 *  Reports invalid flag, flags that supported neither by R8 nor Proguard.
 */
class ProguardR8InvalidFlagInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitFlag(flag: ProguardR8Flag) {
        super.visitFlag(flag)
        val flagText = flag.text.substring(1)
        if (!R8_FLAGS.contains(flagText) && !PROGUARD_FLAGS.contains(flagText)) {
          holder.registerProblem(flag, "Invalid flag", ProblemHighlightType.ERROR)
        }
      }
    }
  }
}

/**
 *  Reports flags supported by Proguard, but ignored by R8.
 */
class ProguardR8IgnoredFlagInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitFlag(flag: ProguardR8Flag) {
        super.visitFlag(flag)
        if (flag.getModuleSystem()?.codeShrinker == CodeShrinker.R8) {
          val flagText = flag.text.substring(1)
          if (!R8_FLAGS.contains(flagText) && PROGUARD_FLAGS.contains(flagText)) {
            holder.registerProblem(flag, "Flag ignored by R8", ProblemHighlightType.WARNING)
          }
        }
      }
    }
  }
}
