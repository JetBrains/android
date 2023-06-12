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
package com.android.tools.idea.lang.databinding.completion

import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.findImportTag
import com.android.tools.idea.databinding.util.findVariableTag
import com.android.tools.idea.lang.databinding.config.DbFile
import com.android.tools.idea.lang.databinding.getBindingIndexEntry
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_LAMBDA
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_METHOD_REFERENCE
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.UNKNOWN_CONTEXT
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_ACCEPTED
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_SUGGESTED
import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext

/**
 * This handles completion in the data binding expressions (inside `@{}`).
 *
 *
 * Completion for everything under `<data>` tag is in
 * [org.jetbrains.android.AndroidXmlCompletionContributor.completeDataBindingTypeAttr].
 */
open class DataBindingCompletionContributor : CompletionContributor() {

  private val onCompletionHandler: InsertHandler<LookupElement>? = InsertHandler { context, lookupElement ->
    moveCaretInsideMethodParenthesis(lookupElement, context)
    trackCompletionAccepted(context)
  }

  private fun trackCompletionAccepted(context: InsertionContext) {
    val tracker = DataBindingTracker.getInstance(context.project)

    val childElement = context.file.findElementAt(context.startOffset)!!
    when (childElement.parent.parent) {
      is PsiDbFunctionRefExpr -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED,
                                                                    DATA_BINDING_CONTEXT_METHOD_REFERENCE)
      is PsiDbRefExpr -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, DATA_BINDING_CONTEXT_LAMBDA)
      else -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, UNKNOWN_CONTEXT)
    }
  }

  private fun moveCaretInsideMethodParenthesis(lookupElement: LookupElement,
                                               context: InsertionContext) {
    val psiMethod = lookupElement.psiElement as? PsiMethod
    if (psiMethod != null
        && context.file.findElementAt(context.startOffset)?.let { getDataBindingExpressionFromPosition(it) } is PsiDbRefExpr) {
      ParenthesesInsertHandler.getInstance(psiMethod.hasParameters()).handleInsert(context, lookupElement)
    }
  }

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        // During first invocation, only suggest valid options. During subsequent invocations, also suggest invalid options
        // such as private members or instance methods on class objects.
        val onlyValidCompletions = parameters.invocationCount <= 1

        val tracker = DataBindingTracker.getInstance(parameters.editor.project!!)

        val position = parameters.originalPosition ?: parameters.position

        val parent = position.parent
        if (position.parent.references.isEmpty()) {
          // try to replace parent
          val dataBindingExpression = getDataBindingExpressionFromPosition(position)
          if (dataBindingExpression is PsiDbRefExpr) {
            val ownerExpr = dataBindingExpression.expr
            if (ownerExpr == null) {
              autoCompleteVariablesAndUnqualifiedFunctions(getFile(dataBindingExpression), result)
              return
            }
            result.addAllElements(populatePackageReferenceCompletions(ownerExpr))
            result.addAllElements(populateInnerClassReferenceCompletions(ownerExpr, onlyValidCompletions))
            result.addAllElements(populateFieldReferenceCompletions(ownerExpr, onlyValidCompletions))
            result.addAllElements(populateMethodReferenceCompletions(ownerExpr, onlyValidCompletions))
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_LAMBDA)
          }
          else if (dataBindingExpression is PsiDbFunctionRefExpr) {
            result.addAllElements(populateMethodReferenceCompletions(dataBindingExpression.expr, onlyValidCompletions))
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_METHOD_REFERENCE)
          }
        }
        else {
          //TODO(b/129497876): improve completion experience for variables and static functions
          result.addAllElements(populatePackageReferenceCompletions(parent))
          result.addAllElements(populateInnerClassReferenceCompletions(parent, onlyValidCompletions))
          result.addAllElements(populateFieldReferenceCompletions(parent, onlyValidCompletions))
          result.addAllElements(populateMethodReferenceCompletions(parent, onlyValidCompletions))
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, UNKNOWN_CONTEXT)
        }
      }
    })
  }

  /**
   * Returns the parent expression wrapping the target [PsiElement].
   *
   * Note: [element] is a PsiElement identifier. Its parent is a PsiDbId, which is a child of the overall expression, whose type we use
   * to choose what kind of completion logic to carry out. For example user types @{model::g<caret>}. Position is the LeafPsiElement "g".
   * Parent is the PsiDbId "g". Grandparent is the whole expression "model:g".
   */
  private fun getDataBindingExpressionFromPosition(element: PsiElement) = element.parent.parent

  private fun getFile(element: PsiElement): DbFile {
    var result = element
    while (result !is DbFile) {
      result = result.parent ?: throw IllegalArgumentException()
    }
    return result
  }

  private fun autoCompleteVariablesAndUnqualifiedFunctions(file: DbFile, result: CompletionResultSet) {
    autoCompleteUnqualifiedFunctions(result)

    val indexEntry = getBindingIndexEntry(file) ?: return

    val project = file.project
    val xmlFile = DataBindingUtil.findXmlFile(project, indexEntry.file) ?: return
    val variableTagNamePairs = indexEntry.data.variables.map { variable ->
      variable.name to xmlFile.findVariableTag(variable.name)
    }
    val importTagTypePairs = indexEntry.data.imports.map { import ->
      import.shortName to xmlFile.findImportTag(import.shortName)
    }

    result.addAllElements((variableTagNamePairs + importTagTypePairs).mapNotNull { nameToTag ->
      val xmlTag = nameToTag.second ?: return@mapNotNull null
      val name = nameToTag.first
      LookupElementBuilder.create(xmlTag, DataBindingUtil.convertVariableNameToJavaFieldName(name)).withInsertHandler(onCompletionHandler)
    })

    val module = file.androidFacet?.module ?: return
    JavaPsiFacade.getInstance(project).findPackage(CommonClassNames.DEFAULT_PACKAGE)
      ?.getClasses(ModulesScope.moduleWithLibrariesScope(module))
      ?.forEach {
        result.addElement(JavaLookupElementBuilder.forClass(it, it.name, true).withInsertHandler(onCompletionHandler))
      }

    // Add completions for sub packages from root e.g. "com", "androidx", etc.
    JavaPsiFacade.getInstance(project).findPackage("")
      ?.getSubPackages(ModulesScope.moduleWithLibrariesScope(module))
      ?.filter { pkg -> pkg.getSubPackages(module.moduleScope).isNotEmpty() || pkg.getClasses(module.moduleScope).isNotEmpty() }
      ?.filter { pkg -> pkg.name?.all { char -> Character.isJavaIdentifierPart(char) } == true }
      ?.forEach {
        result.addElement(LookupElementBuilder.createWithIcon(it).withInsertHandler(onCompletionHandler).withTypeDecorator(TailType.DOT))
      }
  }

  private fun autoCompleteUnqualifiedFunctions(result: CompletionResultSet) {
    result.addElement(LookupElementBuilder.create("safeUnbox").withInsertHandler(onCompletionHandler))
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are the class or package references of the given expression,
   * which is particularly useful for packages, e.g. `com.android`, `com.intellij.psi.PsiClass`, etc.
   */
  private fun populatePackageReferenceCompletions(referenceExpression: PsiElement): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      val psiPackage = reference.resolve() as? PsiPackage ?: continue
      for (subPackage in psiPackage.subPackages) {
        completionSuggestionsList.add(
          LookupElementBuilder.createWithIcon(subPackage).withInsertHandler(onCompletionHandler).withTypeDecorator(TailType.DOT))
      }
      for (subClass in psiPackage.classes) {
        completionSuggestionsList.add(LookupElementBuilder.createWithIcon(subClass).withInsertHandler(onCompletionHandler))
      }
    }
    return completionSuggestionsList
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are the inner class references of the given expression,
   * which is particularly useful for resource ids, e.g. `R.string`, `R.drawable`, etc.
   * If [onlyValidCompletions] is false, private and mismatched context fields are also suggested.
   */
  private fun populateInnerClassReferenceCompletions(referenceExpression: PsiElement, onlyValidCompletions: Boolean): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      val ref = reference as? ModelClassResolvable ?: continue
      val resolvedType = ref.resolvedType?.unwrapped ?: continue
      val allInnerClasses = resolvedType.psiClass?.allInnerClasses ?: continue
      for (innerClass in allInnerClasses) {
        if (onlyValidCompletions && !innerClass.hasModifierProperty(PsiModifier.PUBLIC)) {
          continue
        }
        completionSuggestionsList.add(JavaLookupElementBuilder
                                        .forClass(innerClass, innerClass.name, true)
                                        .withInsertHandler(onCompletionHandler))
      }
    }
    return completionSuggestionsList
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are the field references of the given expression.
   * If [onlyValidCompletions] is false, private and mismatched context fields are also suggested.
   */
  private fun populateFieldReferenceCompletions(referenceExpression: PsiElement, onlyValidCompletions: Boolean): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      val ref = reference as? ModelClassResolvable ?: continue
      val resolvedType = ref.resolvedType?.unwrapped ?: continue
      for (psiModelField in resolvedType.allFields) {
        if (onlyValidCompletions) {
          if (!psiModelField.isPublic || !ref.memberAccess.accept(psiModelField)) {
            continue
          }
        }
        completionSuggestionsList.addSuggestion(psiModelField.psiField, resolvedType.psiClass, resolvedType.substitutor)
      }
    }
    return completionSuggestionsList
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are method references of the given expression.
   * If [onlyValidCompletions] is false, private and mismatched context fields are also suggested.
   */
  private fun populateMethodReferenceCompletions(referenceExpression: PsiElement,
                                                 onlyValidCompletions: Boolean): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      if (reference is ModelClassResolvable) {
        val ref = reference as ModelClassResolvable
        val resolvedType = ref.resolvedType?.unwrapped ?: continue
        for (psiModelMethod in resolvedType.allMethods) {
          val psiMethod = psiModelMethod.psiMethod
          if (psiMethod.isConstructor) {
            continue
          }
          else if (onlyValidCompletions) {
            if (!psiModelMethod.isPublic || !ref.memberAccess.accept(psiModelMethod)) {
              continue
            }
          }

          // Getter methods are converted to fields inside data binding expressions; so although
          // we are fed PsiMethods, we may convert some of them to PsiFields
          var psiConvertedField: PsiField? = null

          if (DataBindingUtil.isGetter(psiMethod) || DataBindingUtil.isBooleanGetter(psiMethod)) {
            val name = DataBindingUtil.stripPrefixFromMethod(psiMethod)
            psiConvertedField = LightFieldBuilder(name, psiMethod.returnType!!, psiMethod.navigationElement)
            psiConvertedField.containingClass = psiMethod.containingClass
            // Set this explicitly or otherwise the icon comes out as "V" for variable
            psiConvertedField.setBaseIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Field))
            psiConvertedField.setModifierList(LightModifierList(psiMethod))
          }
          if (psiConvertedField == null) {
            completionSuggestionsList.addSuggestion(psiMethod, resolvedType.psiClass, resolvedType.substitutor)
          }
          else {
            completionSuggestionsList.addSuggestion(psiConvertedField, resolvedType.psiClass, resolvedType.substitutor, psiMethod)
          }
        }
      }
    }
    return completionSuggestionsList
  }

  /**
   * @param qualifierClass class that is the active context for this lookup, allowing fields in
   *   the current class (vs. a base class) to be bolded
   * @param fromMethod optionally, a method that this field was synthetically generated from; if
   *   present, it will be mentioned in the lookup as the field's source.
   */
  private fun MutableList<LookupElement>.addSuggestion(psiField: PsiField,
                                                       qualifierClass: PsiClass?,
                                                       substitutor: PsiSubstitutor,
                                                       fromMethod: PsiMethod? = null) {
    var lookupBuilder = JavaLookupElementBuilder
      .forField(psiField, psiField.name, qualifierClass)
      .withTypeText(PsiFormatUtil.formatVariable(psiField, PsiFormatUtilBase.SHOW_TYPE, substitutor))
      .withInsertHandler(onCompletionHandler)

    fromMethod?.presentation?.presentableText?.let { methodText ->
      lookupBuilder = lookupBuilder.withTailText(" (from $methodText)", true)
    }

    add(lookupBuilder)
  }

  /**
   * @param qualifierClass class that is the active context for this lookup, allowing methods in
   *   the current class (vs. a base class) to be bolded
   */
  private fun MutableList<LookupElement>.addSuggestion(psiMethod: PsiMethod, qualifierClass: PsiClass?, substitutor: PsiSubstitutor) {
    val lookupBuilder = JavaLookupElementBuilder
      .forMethod(psiMethod, psiMethod.name, substitutor, qualifierClass)
      .withInsertHandler(onCompletionHandler)
    add(lookupBuilder)
  }
}
