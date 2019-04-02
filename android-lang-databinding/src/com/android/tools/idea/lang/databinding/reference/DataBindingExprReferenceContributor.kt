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
package com.android.tools.idea.lang.databinding.reference

import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.lang.databinding.config.DbFileType
import com.android.tools.idea.lang.databinding.JAVA_LANG
import com.android.tools.idea.lang.databinding.model.PsiCallable
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.model.PsiModelMethod
import com.android.tools.idea.lang.databinding.model.toModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbId
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.res.DataBindingLayoutInfo
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import org.jetbrains.android.dom.converters.DataBindingVariableTypeConverter
import org.jetbrains.android.facet.AndroidFacet

/**
 * For references found inside DataBinding expressions. For references inside `<data>` tags,
 * see [DataBindingVariableTypeConverter].
 */
class DataBindingExprReferenceContributor : PsiReferenceContributor() {
  // TODO: Support generics
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbId::class.java), IdReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbRefExpr::class.java), RefExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbCallExpr::class.java), CallExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbFunctionRefExpr::class.java), FunctionRefExprReferenceProvider())
  }

  /**
   * Provide references for when the user's caret is pointing at PSI for an identifier.
   *
   * From db.bnf: `id ::= IDENTIFIER`
   *
   * (where `IDENTIFIER` means anything that looks like a variable name)
   *
   * Example: `mo<caret>del.doSomething()`
   */
  private inner class IdReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val idName = element.text ?: return PsiReference.EMPTY_ARRAY
      return getReferencesBySimpleName(element, idName)
    }
  }

  /**
   * Provide references for when the user's caret is pointing at PSI for an ref expression, which
   * is, essentially, anything that looks like a fully qualified name.
   *
   * From db.bnf: `refExpr ::= (expr .)? id`
   *
   * Example: `com.exam<caret>ple.databinding.Model`
   */
  private inner class RefExprReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val refExpr = element as PsiDbRefExpr

      // If this expression is actually not qualified, e.g. "value" or "String", return references
      // to just the 'id' portion.
      val prefixExpr = refExpr.expr ?: return getReferencesBySimpleName(element, refExpr.id.text)

      // If we don't find a resolved type, it means that the user is referencing a (fully
      // qualified) class in their expression without a corresponding <import> or <variable>
      // declaration. In that case, we search the current module to see if we can find it directly.
      val modelResolvable = prefixExpr.toModelClassResolvable()
      val psiModelClass = modelResolvable?.resolvedType?.unwrapped ?: return findFullyQualifiedReference(refExpr)

      val fieldText = refExpr.id.text
      if (fieldText.isBlank()) {
        return PsiReference.EMPTY_ARRAY
      }

      // Resolve fully qualified methods / fields, e.g. "variable.value" or "variable.method"
      // TODO: Search for methods with args also. The following only searches for methods with no args.
      //  This results in attributes like 'android:onClick="@{variable.method}"' being left unresolved.
      val psiClass = psiModelClass.psiClass ?: return PsiReference.EMPTY_ARRAY
      val getterOrField = psiModelClass.findGetterOrField(fieldText, modelResolvable.isStatic)


      // TODO: If psiClass is ObservableField<Foo> or ObservablePrimitive, change it to Foo (by an implicit call to #get()).
      when (getterOrField?.type) {
        PsiCallable.Type.METHOD -> {
          val methodsByName = psiClass.findMethodsByName(getterOrField.name, true)
          if (methodsByName.isNotEmpty()) {
            return arrayOf(PsiMethodReference(refExpr, methodsByName[0]))
          }
        }
        PsiCallable.Type.FIELD -> {
          val fieldsByName = psiClass.findFieldByName(getterOrField.name, true)
          if (fieldsByName != null) {
            return arrayOf(PsiFieldReference(refExpr, fieldsByName))
          }
        }
      }

      // Perhaps this field references an inner class?
      val module = ModuleUtilCore.findModuleForPsiElement(element)
      if (module != null) {
        val innerName = "${psiClass.qualifiedName}.$fieldText"
        val innerClass = JavaPsiFacade.getInstance(element.getProject()).findClass(innerName,
                                                                                   module.getModuleWithDependenciesAndLibrariesScope(false))
        if (innerClass != null) {
          return arrayOf(PsiClassReference(element, innerClass))
        }
      }
      return PsiReference.EMPTY_ARRAY
    }

    /**
     * Given an expression like `a.b.c.e.MyClass`, return all matching references to it,
     */
    private fun findFullyQualifiedReference(refExpr: PsiDbRefExpr): Array<PsiReference> {
      val qualifiedExpr = refExpr.expr!!
      val references = qualifiedExpr.references
      if (references.isEmpty()) {
        return PsiReference.EMPTY_ARRAY
      }

      val fieldText = refExpr.id.text
      if (fieldText.isBlank()) return PsiReference.EMPTY_ARRAY

      val module = ModuleUtilCore.findModuleForPsiElement(refExpr) ?: return PsiReference.EMPTY_ARRAY
      val scope = module.getModuleWithDependenciesAndLibrariesScope(false)

      fun fieldMatchesPackage(field: String, aPackage: PsiPackage) = aPackage.name!!.substringAfterLast('.') == field

      references
        .filterIsInstance<PsiPackageReference>()
        .mapNotNull { reference -> reference.resolve() }
        .forEach { aPackage ->
          for (subPackage in aPackage.getSubPackages(scope)) {
            if (fieldMatchesPackage(fieldText, subPackage)) {
              return arrayOf(PsiPackageReference(refExpr, subPackage))
            }
          }
          val classes = aPackage.findClassByShortName(fieldText, scope)
          if (classes.isNotEmpty()) {
            return classes
              .map { aClass -> PsiClassReference(refExpr, aClass) }
              .toTypedArray()
          }
        }
      return PsiReference.EMPTY_ARRAY
    }
  }

  /**
   * Provide references for when the user's caret is pointing at PSI for an call expression, or
   * essentially, any part of a method call.
   *
   * From db.bnf:
   *
   * ```
   * callExpr ::= refExpr '(' expressionList? ')'
   * expressionList ::= expr (',' expr)*
   * ```
   *
   * Example: `model.do<caret>Something()`
   */
  private class CallExprReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val callExpr = element as PsiDbCallExpr
      val methodExpr = callExpr.refExpr.expr ?: return PsiReference.EMPTY_ARRAY
      val psiModelClass = methodExpr.toModelClassResolvable()?.resolvedType?.unwrapped ?: return PsiReference.EMPTY_ARRAY

      val methodArgs: MutableList<PsiModelClass?> = mutableListOf()
      callExpr.expressionList?.exprList?.forEach { expr -> methodArgs.add(expr.toModelClassResolvable()?.resolvedType) }

      // First, see if we can find a name match that also matches all args
      if (!methodArgs.contains(null)) {
        @Suppress("NAME_SHADOWING") // We reframe List<PsiModelClass?> as List<PsiModelClass>
        val methodArgs = methodArgs.requireNoNulls()
        val method = psiModelClass.getMethod(callExpr.refExpr.id.text, methodArgs, staticOnly = false, allowProtected = false)
        if (method is PsiModelMethod) {
          return arrayOf(PsiMethodReference(callExpr, method.psiMethod))
        }
      }

      // As a fallback, see if we can find a method by just its name
      return psiModelClass.findMethods(callExpr.refExpr.id.text, staticOnly = false)
        .map { modelMethod -> PsiMethodReference(callExpr, modelMethod.psiMethod) }
        .toTypedArray()
    }
  }

  /**
   * Provide references for when the user's caret is pointing at PSI for a method reference
   * expression.
   *
   * From db.bnf:
   *
   * ```
   * functionRefExpr ::= expr '::' id
   * ```
   *
   * Example: `Model::do<caret>StaticSomething`
   * Example: `modelInstance::do<caret>Something`
   */
  private class FunctionRefExprReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val funRefExpr = element as PsiDbFunctionRefExpr
      val classExpr = funRefExpr.expr
      val methodExpr = funRefExpr.id
      val psiModelClass = classExpr.toModelClassResolvable()?.resolvedType?.unwrapped ?: return PsiReference.EMPTY_ARRAY

      return psiModelClass.findMethods(methodExpr.text, staticOnly = false)
        .map { modelMethod -> PsiMethodReference(element, modelMethod.psiMethod) }
        .toTypedArray()
    }
  }

  /**
   * Return the parent XML layout info for the target [element], or `null` if that isn't possible
   * (e.g. databinding isn't enabled for this module)
   */
  private fun getParentLayoutInfo(module: Module, element: PsiElement): DataBindingLayoutInfo? {
    val facet = AndroidFacet.getInstance(module)?.takeIf { facet -> DataBindingUtil.isDataBindingEnabled(facet) }
                ?: return null
    val moduleResources = ResourceRepositoryManager.getModuleResources(facet)

    var topLevelFile = InjectedLanguageManager.getInstance(element.project).getTopLevelFile(element) ?: return null
    if (topLevelFile === DbFileType.INSTANCE) {
      // If this is a DbFileType file, it's probably contained in another (XML) file that's
      // our real top-level file.
      topLevelFile.context?.containingFile?.let { topLevelFile = it }
    }

    val fileNameWithoutExtension = topLevelFile.name.substringBeforeLast('.')
    return moduleResources.getDataBindingLayoutInfo(fileNameWithoutExtension)
  }

  /**
   * Given a [PsiElement] and its simpleName (e.g. name without any qualified prefix), attempt to find
   * a reference for it. This code is shared by the reference contributors for ID expressions and
   * qualified ID (refExpr) expressions.
   */
  private fun getReferencesBySimpleName(element: PsiElement, simpleName: String): Array<PsiReference> {
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return PsiReference.EMPTY_ARRAY

    // Search the parent layout file, as that will let us check if the current identifier is found
    // somewhere in the <data> section
    run {
      val layoutInfo = getParentLayoutInfo(module, element) ?: return PsiReference.EMPTY_ARRAY

      layoutInfo.getItems(DataBindingResourceType.VARIABLE).values
        .firstOrNull { variable -> simpleName == variable.name }
        ?.let { return arrayOf(XmlVariableReference(element, it.xmlTag, it, layoutInfo, module)) }

      layoutInfo.getItems(DataBindingResourceType.IMPORT).values
        .firstOrNull { import -> simpleName == DataBindingUtil.getAlias(import) }
        ?.let { return arrayOf(XmlImportReference(element, it.xmlTag, it, module)) }
    }

    // If we're here, we couldn't find a reference inside the XML file. Now, we have to search
    // the user's codebase.
    run {
      val javaPsiFacade = JavaPsiFacade.getInstance(element.project)

      // If the id is an unqualified java.lang class name (e.g. "String", "Integer"), handle it
      // even if not explicitly imported
      simpleName
        .takeUnless { it.contains('.') } // Searching unqualified classes only; no packages!
        ?.let { name -> javaPsiFacade.findClass(JAVA_LANG + name, GlobalSearchScope.moduleWithLibrariesScope(module)) }
        ?.let { langClass -> return arrayOf(PsiClassReference(element, langClass)) }

      val aPackage = javaPsiFacade.findPackage(simpleName) ?: return PsiReference.EMPTY_ARRAY
      return arrayOf(PsiPackageReference(element, aPackage))
    }
  }
}
