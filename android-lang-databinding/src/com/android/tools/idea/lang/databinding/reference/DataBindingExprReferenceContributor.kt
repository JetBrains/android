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

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.lang.databinding.JAVA_LANG
import com.android.tools.idea.lang.databinding.config.DbFileType
import com.android.tools.idea.lang.databinding.model.PsiCallable
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.model.PsiModelField
import com.android.tools.idea.lang.databinding.model.PsiModelMethod
import com.android.tools.idea.lang.databinding.model.toModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameter
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameterList
import com.android.tools.idea.lang.databinding.psi.PsiDbLambdaExpression
import com.android.tools.idea.lang.databinding.psi.PsiDbLiteralExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.binding.BindingLayoutInfo
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlAttribute
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
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbRefExpr::class.java), RefExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbCallExpr::class.java), CallExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbFunctionRefExpr::class.java), FunctionRefExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbLiteralExpr::class.java), LiteralExprReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbInferredFormalParameter::class.java),
                                        InferredFormalParameterReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbLambdaExpression::class.java), LambdaExpressionReferenceProvider())
  }

  /**
   * Provides references for [PsiDbRefExpr].
   * There are two kinds of reference expressions:
   * Simple ref expression only contains an identifier.
   * Qualified ref expression is in the form of `a.b.c.d`
   *
   * From db.bnf:
   * `fake refExpr ::= expr? '.' id`
   * `simpleRefExpr ::= id {extends=refExpr elementType=refExpr}`
   * `qualRefExpr ::= expr '.' id {extends=refExpr elementType=refExpr}`
   *
   * Example: `com.example.databinding.Model`, `model` in `model.doSomething()`
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
            return arrayOf(PsiMethodReference(refExpr, PsiModelMethod(psiModelClass, methodsByName[0])))
          }
        }
        PsiCallable.Type.FIELD -> {
          val fieldsByName = psiClass.findFieldByName(getterOrField.name, true)
          if (fieldsByName != null) {
            return arrayOf(PsiFieldReference(refExpr, PsiModelField(psiModelClass, fieldsByName)))
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
     * Given an expression like `a.b.c.e.MyClass`, returns all matching references to it.
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
   * Provides references for [PsiDbCallExpr]
   * From db.bnf:
   *
   * ```
   * callExpr ::= refExpr '(' expressionList? ')'
   * expressionList ::= expr (',' expr)*
   * ```
   *
   * Example: `model.doSomething()`
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
          return arrayOf(PsiMethodReference(callExpr, method))
        }
      }

      // As a fallback, see if we can find a method by just its name
      return psiModelClass.findMethods(callExpr.refExpr.id.text, staticOnly = false)
        .map { modelMethod -> PsiMethodReference(callExpr, modelMethod) }
        .toTypedArray()
    }
  }

  /**
   * Provides references for [PsiDbFunctionRefExpr]
   * From db.bnf:
   *
   * ```
   * functionRefExpr ::= expr '::' id
   * ```
   *
   * Example: `Model::doStaticSomething`
   * Example: `modelInstance::doSomething`
   */
  private class FunctionRefExprReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val funRefExpr = element as PsiDbFunctionRefExpr
      val classExpr = funRefExpr.expr
      val methodExpr = funRefExpr.id
      val psiModelClass = classExpr.toModelClassResolvable()?.resolvedType?.unwrapped ?: return PsiReference.EMPTY_ARRAY
      return psiModelClass.findMethods(methodExpr.text, staticOnly = false)
        .map { modelMethod -> PsiMethodReference(element, modelMethod) }
        .toTypedArray()
    }
  }

  /**
   * Provides references for [PsiDbInferredFormalParameter]
   *
   * From db.bnf:
   *
   * ```
   * inferredFormalParameterList ::= inferredFormalParameter (',' inferredFormalParameter)*
   *
   * inferredFormalParameter ::= IDENTIFIER
   * ```
   *
   * Example: `view1` and `view2` in `@{(view1, view2) -> model.save(view1, view2)}`
   */
  private class InferredFormalParameterReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val lambdaExpression = element.parent.parent.parent ?: return arrayOf()
      val functionalClass = lambdaExpression.references
                              .filterIsInstance<PsiClassReference>()
                              .firstOrNull()
                              ?.resolve() as? PsiClass
                            ?: return arrayOf()
      val listenerMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalClass) ?: return arrayOf()
      // Associate this expression's parameters with the listener method.
      val parameter = element as PsiDbInferredFormalParameter
      val parameterList = parameter.parent as PsiDbInferredFormalParameterList
      val index = parameterList.inferredFormalParameterList.indexOf(parameter)
      val listenerParameter = listenerMethod.parameterList.parameters.getOrNull(index) ?: return arrayOf()
      return arrayOf(PsiParameterReference(element, listenerParameter))
    }
  }

  /**
   * Provides references for [PsiLambdaExpression]
   *
   * From db.bnf:
   *
   * ```
   * lambdaExpression ::= lambdaParameters '->' expr
   * ```
   *
   * Example: `() -> model.doSomething()`, `view -> model.save(view)`
   */
  private inner class LambdaExpressionReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val functionalClass = getFunctionClassFromAssociatedAttribute(element) ?: return arrayOf()
      return arrayOf(PsiClassReference(element, functionalClass))
    }
  }

  /**
   * Provides references for [PsiDbLiteralExpr]
   *
   * From db.bnf:
   *
   * ```
   * private literal
   *  ::= INTEGER_LITERAL
   *  |   FLOAT_LITERAL
   *  |   LONG_LITERAL
   *  |   DOUBLE_LITERAL
   *  |   TRUE | FALSE
   *  |   NULL
   *  |   CHARACTER_LITERAL
   *  |   STRING_LITERAL
   * ```
   *
   * Example: true, false, 123, `str`
   */
  private class LiteralExprReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val literalExpr = element as PsiDbLiteralExpr
      val psiType = when (literalExpr.node.firstChildNode.elementType) {
        DbTokenTypes.INTEGER_LITERAL -> PsiType.INT
        DbTokenTypes.FLOAT_LITERAL -> PsiType.FLOAT
        DbTokenTypes.LONG_LITERAL -> PsiType.LONG
        DbTokenTypes.DOUBLE_LITERAL -> PsiType.DOUBLE
        DbTokenTypes.TRUE, DbTokenTypes.FALSE -> PsiType.BOOLEAN
        DbTokenTypes.NULL -> PsiType.NULL
        DbTokenTypes.CHARACTER_LITERAL -> PsiType.CHAR
        DbTokenTypes.STRING_LITERAL -> DataBindingUtil.parsePsiType("java.lang.String", element.project, null) ?: return arrayOf()
        else -> return arrayOf()
      }
      return arrayOf(PsiLiteralReference(element, psiType))
    }
  }

  /**
   * Returns the functional class if referenced by the associated attribute.
   *
   * e.g. "android:text" is resolved to java.lang.String which is not a functional class.
   *      "android:onClick" is resolved to OnClickListener which is a functional interface(class).
   */
  private fun getFunctionClassFromAssociatedAttribute(element: PsiElement): PsiClass? {
    val attribute = element.containingFile.context?.parent as? XmlAttribute ?: return null
    return attribute.references
      .filterIsInstance<PsiParameterReference>()
      .firstOrNull()
      ?.resolvedType
      ?.psiClass
      ?.takeIf { LambdaUtil.isFunctionalClass(it) }
  }

  /**
   * Returns the parent XML layout info for the target [element], or null if that isn't possible
   * (e.g. databinding isn't enabled for this module).
   */
  private fun getParentLayoutInfo(module: Module, element: PsiElement): BindingLayoutInfo? {
    val facet = AndroidFacet.getInstance(module)?.takeIf { facet -> DataBindingUtil.isDataBindingEnabled(facet) }
                ?: return null
    val moduleResources = ResourceRepositoryManager.getModuleResources(facet)

    var topLevelFile = InjectedLanguageManager.getInstance(element.project).getTopLevelFile(element) ?: return null
    if (topLevelFile === DbFileType.INSTANCE) {
      // If this is a DbFileType file, it's probably contained in another (XML) file that's
      // our real top-level file.
      topLevelFile.context?.containingFile?.let { topLevelFile = it }
    }

    val fileNameWithoutExtension = topLevelFile.name.substringBefore('.')
    return moduleResources.getBindingLayoutInfo(fileNameWithoutExtension).firstOrNull()
  }

  /**
   * Given a [PsiElement] and its [simpleName] (e.g. name without any qualified prefix), attempts to find
   * a reference for it. In case of name collisions variable names take precedence over imported short type names.
   */
  private fun getReferencesBySimpleName(element: PsiElement, simpleName: String): Array<PsiReference> {
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return PsiReference.EMPTY_ARRAY

    // Search the parent layout file, as that will let us check if the current identifier is found
    // somewhere in the <data> section.
    run {
      val layoutInfo = getParentLayoutInfo(module, element) ?: return PsiReference.EMPTY_ARRAY
      val layoutData = layoutInfo.data

      layoutData.findVariable(simpleName)?.let { variable ->
        DataBindingUtil.findVariableTag(layoutData, variable.name)?.let { variableTag ->
          return arrayOf(XmlVariableReference(element, variableTag, variable, layoutInfo, module))
        }
      }

      layoutData.findImport(simpleName)?.let { import ->
        DataBindingUtil.findImportTag(layoutData, simpleName)?.let { importTag ->
          return arrayOf(XmlImportReference(element, importTag, import, module))
        }
      }
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
