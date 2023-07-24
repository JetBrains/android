package org.jetbrains.android

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.isAccessible
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.AndroidDeprecationInspection

class AndroidJavaCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    super.fillCompletionVariants(parameters, resultSet)
    val position = parameters.position
    val facet = AndroidFacet.getInstance(position) ?: return
    val filterPrivateResources = shouldFilterPrivateResources(position, facet)
    resultSet.runRemainingContributors(parameters) { result: CompletionResult? ->
      var modifiedResult = result
      if (filterPrivateResources) {
        if (isForPrivateResource(modifiedResult!!, facet)) {
          modifiedResult = null
        }
      }
      if (modifiedResult != null) {
        modifiedResult = fixDeprecationPresentation(modifiedResult, parameters)
      }
      if (modifiedResult != null) {
        resultSet.passResult(modifiedResult)
      }
    }
  }

  /**
   * Wrapper around a [LookupElement] that removes the deprecation strikeout. It's used when we we are in a code branch specific to
   * an old SDK where a given [PsiElement] was not yet deprecated.
   *
   * @see AndroidDeprecationInspection.DeprecationFilter
   */
  private class NonDeprecatedDecorator(delegate: LookupElement) : LookupElementDecorator<LookupElement?>(delegate) {
    override fun renderElement(presentation: LookupElementPresentation) {
      super.renderElement(presentation)
      presentation.isStrikeout = false
    }
  }

  companion object {
    private val EXCLUDED_PACKAGES = arrayOf("javax.swing", "javafx")
    private fun shouldFilterPrivateResources(position: PsiElement, facet: AndroidFacet): Boolean {
      var filterPrivateResources = false
      // Filter out private resources when completing R.type.name expressions, if any.
      if (position.parent is PsiReferenceExpression) {
        val ref = position.parent as PsiReferenceExpression
        if (ref.qualifierExpression != null &&
          ref.qualifierExpression is PsiReferenceExpression
        ) {
          val ref2 = ref.qualifierExpression as PsiReferenceExpression?
          if (ref2!!.qualifierExpression is PsiReferenceExpression) {
            val ref3 = ref2.qualifierExpression as PsiReferenceExpression?
            if (SdkConstants.R_CLASS == ref3!!.referenceName) {
              // We do the filtering only on the R class of this module, users who explicitly reference other R classes are assumed to know
              // what they're doing.
              val qualifierExpression = ref3.qualifierExpression
              if (qualifierExpression == null) {
                filterPrivateResources = true
              } else if (qualifierExpression is PsiReferenceExpression) {
                val referenceExpression = qualifierExpression
                if (facet.getModuleSystem().getPackageName() == referenceExpression.qualifiedName || facet.getModuleSystem()
                    .getTestPackageName() == referenceExpression.qualifiedName
                ) {
                  filterPrivateResources = true
                }
              }
            }
          }
        }
      }
      return filterPrivateResources
    }

    fun fixDeprecationPresentation(
      result: CompletionResult,
      parameters: CompletionParameters
    ): CompletionResult {
      var result = result
      val obj = result.lookupElement.getObject()
      if (obj is PsiDocCommentOwner) {
        val docCommentOwner = obj
        if (docCommentOwner.isDeprecated) {
          for (filter in AndroidDeprecationInspection.getFilters()) {
            if (filter.isExcluded(docCommentOwner, parameters.position, null)) {
              result = result.withLookupElement(NonDeprecatedDecorator(result.lookupElement))
            }
          }
        }
      }
      return result
    }

    fun isForPrivateResource(result: CompletionResult, facet: AndroidFacet): Boolean {
      val obj = result.lookupElement.getObject() as? PsiField ?: return false
      val psiField = obj
      val containingClass = psiField.containingClass
      if (containingClass != null) {
        val rClass = containingClass.containingClass
        if (rClass != null && SdkConstants.R_CLASS == rClass.name) {
          val resourceTypeName = containingClass.name ?: return false
          val type = ResourceType.fromClassName(containingClass.name!!)
          val repositoryManager = StudioResourceRepositoryManager.getInstance(facet)
          return type != null && !isAccessible(repositoryManager.namespace, type, psiField.name, facet)
        }
      }
      return false
    }

    private fun isAllowedInAndroid(qName: String): Boolean {
      for (aPackage in EXCLUDED_PACKAGES) {
        if (qName.startsWith("$aPackage.")) {
          return false
        }
      }
      return true
    }
  }
}
