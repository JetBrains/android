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
import com.siyeh.IntentionPowerPackBundle
import com.siyeh.ipp.switchtoif.ReplaceSwitchWithIfIntention
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class AndroidNonConstantResIdsInSwitchInspection : LocalInspectionTool() {
  override fun getGroupDisplayName(): @Nls String =
    AndroidBundle.message("android.inspections.group.name")

  override fun getDisplayName(): @Nls String =
    AndroidBundle.message("android.inspections.non.constant.res.ids.in.switch.name")

  override fun getShortName() = "AndroidNonConstantResIdsInSwitch"

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : JavaElementVisitor() {
      override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        val facet = AndroidFacet.getInstance(statement) ?: return
        if (facet.configuration.isAppProject) return

        val caseValue = statement.caseValue as? PsiReferenceExpression ?: return

        val switchStatement = statement.getParentOfType<PsiSwitchStatement>(true) ?: return
        if (!ReplaceSwitchWithIfIntention.canProcess(switchStatement)) return

        val resolvedField = caseValue.resolve() as? PsiField ?: return
        if (!isResourceField(resolvedField)) return

        if (resolvedField.modifierList?.hasModifierProperty(PsiModifier.FINAL) != true) {
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
    get() = getQuickFixName()

  private class MyQuickFix : LocalQuickFix {
    override fun getFamilyName() = getQuickFixName()

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      descriptor.psiElement
        ?.getParentOfType<PsiSwitchStatement>(true)
        ?.let(ConvertSwitchToIfIntention::doProcessIntention)
    }
  }
}

private fun getQuickFixName() =
  IntentionPowerPackBundle.message("replace.switch.with.if.intention.name")
