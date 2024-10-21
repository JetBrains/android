package org.jetbrains.android.inspections

import com.android.tools.idea.res.isResourceField
import com.intellij.codeInsight.daemon.impl.quickfix.ConvertSwitchToIfIntention
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSwitchLabelStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.util.PsiTreeUtil
import com.siyeh.IntentionPowerPackBundle
import com.siyeh.ipp.switchtoif.ReplaceSwitchWithIfIntention
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls

class AndroidNonConstantResIdsInSwitchInspection : LocalInspectionTool() {
  override fun getGroupDisplayName(): @Nls String {
    return AndroidBundle.message("android.inspections.group.name")
  }

  override fun getDisplayName(): @Nls String {
    return AndroidBundle.message("android.inspections.non.constant.res.ids.in.switch.name")
  }

  override fun getShortName(): String {
    return "AndroidNonConstantResIdsInSwitch"
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : JavaElementVisitor() {
      override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        val facet = AndroidFacet.getInstance(statement)
        if (facet == null || facet.configuration.isAppProject) {
          return
        }

        val caseValue = statement.caseValue as? PsiReferenceExpression ?: return

        val switchStatement = PsiTreeUtil.getParentOfType(statement, PsiSwitchStatement::class.java)
        if (switchStatement == null || !ReplaceSwitchWithIfIntention.canProcess(switchStatement)) {
          return
        }

        val resolvedElement = caseValue.resolve()
        if (resolvedElement == null || resolvedElement !is PsiField) {
          return
        }

        val resolvedField = resolvedElement
        if (!isResourceField(resolvedField)) {
          return
        }

        val modifierList = resolvedField.modifierList

        if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          holder.registerProblem(
            caseValue,
            AndroidBundle.message("android.inspections.non.constant.res.ids.in.switch.message"),
            MyQuickFix(),
          )
        }
      }
    }
  }

  val quickFixName: String
    get() = IntentionPowerPackBundle.message("replace.switch.with.if.intention.name")

  private inner class MyQuickFix : LocalQuickFix {
    override fun getFamilyName(): String {
      return this.quickFixName
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement ?: return

      val switchStatement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement::class.java)
      if (switchStatement == null) {
        return
      }

      ConvertSwitchToIfIntention.doProcessIntention(switchStatement)
    }
  }
}
