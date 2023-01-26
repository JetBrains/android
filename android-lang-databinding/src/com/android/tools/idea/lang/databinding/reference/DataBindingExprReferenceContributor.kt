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

import com.android.SdkConstants
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil
import com.android.tools.idea.databinding.util.findIdAttribute
import com.android.tools.idea.databinding.util.findImportTag
import com.android.tools.idea.databinding.util.findVariableTag
import com.android.tools.idea.lang.databinding.JAVA_LANG
import com.android.tools.idea.lang.databinding.config.DbFileType
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.model.PsiModelClass.MemberAccess.ALL_MEMBERS
import com.android.tools.idea.lang.databinding.model.PsiModelClass.MemberAccess.STATICS_ONLY
import com.android.tools.idea.lang.databinding.model.PsiModelField
import com.android.tools.idea.lang.databinding.model.PsiModelMethod
import com.android.tools.idea.lang.databinding.model.toModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameter
import com.android.tools.idea.lang.databinding.psi.PsiDbInferredFormalParameterList
import com.android.tools.idea.lang.databinding.psi.PsiDbLambdaParameters
import com.android.tools.idea.lang.databinding.psi.PsiDbLiteralExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbResourcesExpr
import com.android.tools.idea.lang.databinding.resolveScopeWithResources
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.ProcessingContext
import org.jetbrains.android.dom.converters.DataBindingVariableTypeConverter
import org.jetbrains.android.dom.resources.ResourceValue
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
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbLambdaParameters::class.java), LambdaParametersReferenceProvider())
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbResourcesExpr::class.java), ResourceReferenceProvider())
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
      // Do not add similar references twice if the element is within a [PsiDbCallExpr]. e.g. "var.method" in "var.method()".
      if (element.parent is PsiDbCallExpr) {
        return PsiReference.EMPTY_ARRAY
      }

      val refExpr = element as PsiDbRefExpr

      // If this expression is actually not qualified, e.g. "value" or "String", return references
      // to just the 'id' portion.
      val prefixExpr = refExpr.expr ?: return getReferencesFromSimpleRefExpr(element, refExpr.id.text)

      return getReferencesFromQualRefExpr(element, prefixExpr, refExpr.id.text)
    }

    /**
     * Returns the index entries that might have information about the target [element],
     * or null if that isn't possible (e.g. databinding isn't enabled for this module).
     */
    private fun getBindingIndexEntries(module: Module, element: PsiElement): Iterable<BindingXmlIndex.Entry>? {
      AndroidFacet.getInstance(module)?.takeIf { facet -> DataBindingUtil.isDataBindingEnabled(facet) } ?: return null
      var topLevelFile = InjectedLanguageManager.getInstance(element.project).getTopLevelFile(element) ?: return null
      if (topLevelFile === DbFileType.INSTANCE) {
        // If this is a DbFileType file, it's probably contained in another (XML) file that's
        // our real top-level file.
        topLevelFile.context?.containingFile?.let { topLevelFile = it }
      }

      val fileNameWithoutExtension = topLevelFile.name.substringBefore('.')
      return BindingXmlIndex.getEntriesForLayout(module, fileNameWithoutExtension).takeIf { it.isNotEmpty() }
    }

    /**
     * Given a [PsiElement] and its [simpleName] (e.g. name without any qualified prefix), attempts to find
     * a reference for it. In case of name collisions variable names take precedence over imported short type names.
     */
    private fun getReferencesFromSimpleRefExpr(element: PsiElement, simpleName: String): Array<PsiReference> {
      val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return PsiReference.EMPTY_ARRAY

      run {
        // Search potential layout files to find the identifier.
        getBindingIndexEntries(module, element)?.forEach { entry ->
          val xmlFile = DataBindingUtil.findXmlFile(element.project, entry.file) ?: return@forEach
          val bindingData = entry.data
          bindingData.findVariable(simpleName)?.let { variable ->
            xmlFile.findVariableTag(variable.name)?.let { variableTag ->
              return arrayOf(XmlVariableReference(element, variableTag, variable, bindingData, module))
            }
          }

          bindingData.findImport(simpleName)?.let { import ->
            xmlFile.findImportTag(simpleName)?.let { importTag ->
              return arrayOf(XmlImportReference(element, importTag, import))
            }
          }

          val attribute = bindingData.viewIds
            .firstOrNull { simpleName == DataBindingUtil.convertAndroidIdToJavaFieldName(it.id) }
            ?.let { xmlFile.findIdAttribute(it.id) }
          if (attribute != null) {
            return arrayOf(XmlAttributeReference(element, attribute))
          }
        }
      }

      // If we're here, we couldn't find a reference inside the XML file. Now, we have to search
      // the user's codebase.
      run {
        val javaPsiFacade = JavaPsiFacade.getInstance(element.project)

        // If the id is an unqualified java.lang class name (e.g. "String", "Integer"), handle it
        // even if not explicitly imported
        // Searching unqualified classes only; no packages!
        val langClass = simpleName.takeUnless { name -> name.contains('.') }
          ?.let { name -> javaPsiFacade.findClass(JAVA_LANG + name, GlobalSearchScope.moduleWithLibrariesScope(module)) }
        if (langClass != null) {
          return arrayOf(PsiClassReference(element, langClass, STATICS_ONLY))
        }

        val psiPackage = javaPsiFacade.findPackage(simpleName)
        if (psiPackage != null && element is PsiDbRefExpr) {
          return arrayOf(PsiPackageReference(element, psiPackage))
        }

        // Add a reference to Context class for "context" when no other references can be found.
        if (simpleName == "context") {
          val contextClass = LayoutBindingTypeUtil.parsePsiType(SdkConstants.CLASS_CONTEXT, element)
            ?.let { psiType -> (psiType as? PsiClassType)?.resolve() }
          if (contextClass != null) {
            return arrayOf(PsiClassReference(element, contextClass, ALL_MEMBERS))
          }
        }
      }

      return PsiReference.EMPTY_ARRAY
    }

    private fun getReferencesFromQualRefExpr(refExpr: PsiDbRefExpr, prefixExpr: PsiDbExpr, fieldText: String): Array<PsiReference> {
      // If we don't find a resolved type, it means that the user is referencing a (fully
      // qualified) class in their expression without a corresponding <import> or <variable>
      // declaration. In that case, we search the current module to see if we can find it directly.
      val modelResolvable = prefixExpr.toModelClassResolvable()
      val psiModelClass = modelResolvable?.resolvedType?.unwrapped ?: return findReferencesByFullyQualifiedClassName(refExpr)

      if (fieldText.isBlank()) {
        return PsiReference.EMPTY_ARRAY
      }

      // Resolve fully qualified methods / fields, e.g. "variable.value" or "variable.method"
      val psiClass = psiModelClass.psiClass ?: return PsiReference.EMPTY_ARRAY

      // Find the reference to a field or its getter e.g. "var.field" may reference "var.field", "var.isField()" or "var.getField()".
      when (val getterOrField =
        psiModelClass.findGetterOrField(fieldText, modelResolvable.memberAccess)) {
        is PsiModelMethod -> {
          val getterReference = PsiMethodReference(refExpr, getterOrField, PsiMethodReference.Kind.METHOD_CALL)
          // Find the reference to setter method that has the same pattern and type.
          // e.g. `String getName()` and `setName(String)`
          val setterReference = getterOrField.returnType
            ?.let { type -> psiModelClass.findSetter(fieldText, type) }
            ?.let { setterMethod -> PsiMethodReference(refExpr, setterMethod, PsiMethodReference.Kind.METHOD_REFERENCE) }
          return if (setterReference != null) arrayOf(getterReference, setterReference) else arrayOf(getterReference)
        }
        is PsiModelField -> {
          return arrayOf(PsiFieldReference(refExpr, getterOrField))
        }
      }

      // Find the reference to a listener method without parentheses. e.g. "var.onClick".
      val methods = psiModelClass.findMethods(fieldText, modelResolvable.memberAccess)
      if (methods.isNotEmpty()) {
        return methods.map { modelMethod ->
          PsiMethodReference(refExpr, modelMethod, PsiMethodReference.Kind.METHOD_REFERENCE)
        }.toTypedArray()
      }

      // Find the reference to an inner class.
      val innerClass = psiClass.findInnerClassByName(fieldText, true)
      if (innerClass != null && innerClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return arrayOf(PsiClassReference(refExpr, innerClass, STATICS_ONLY))
      }

      // Find the reference to get() method if [psiModelClass] is an instance of [java.util.Map] and we can not find any other references.
      if (LayoutBindingTypeUtil.parsePsiType("java.util.Map", refExpr)?.isAssignableFrom(psiModelClass.type) == true) {
        val getMethod = psiClass.findMethodsByName("get", true).firstOrNull { it.parameterList.parametersCount == 1 }
        if (getMethod != null) {
          return arrayOf(PsiMethodReference(refExpr, PsiModelMethod(psiModelClass, getMethod), PsiMethodReference.Kind.METHOD_CALL))
        }
      }
      return PsiReference.EMPTY_ARRAY
    }

    /**
     * Given an expression like `a.b.c.e.MyClass`, returns all matching references to it.
     */
    private fun findReferencesByFullyQualifiedClassName(refExpr: PsiDbRefExpr): Array<PsiReference> {
      val qualifiedExpr = refExpr.expr!!
      val references = qualifiedExpr.references
      if (references.isEmpty()) {
        return PsiReference.EMPTY_ARRAY
      }

      val fieldText = refExpr.id.text
      if (fieldText.isBlank()) return PsiReference.EMPTY_ARRAY

      val scope = refExpr.resolveScopeWithResources ?: refExpr.resolveScope

      fun fieldMatchesPackage(field: String, aPackage: PsiPackage) = aPackage.name!!.substringAfterLast('.') == field

      references
        .filterIsInstance<PsiPackageReference>()
        .mapNotNull { reference -> reference.resolve() as? PsiPackage }
        .forEach { aPackage ->
          for (subPackage in aPackage.getSubPackages(scope)) {
            if (fieldMatchesPackage(fieldText, subPackage)) {
              return arrayOf(PsiPackageReference(refExpr, subPackage))
            }
          }
          val classes = aPackage.findClassByShortName(fieldText, scope)
          if (classes.isNotEmpty()) {
            return classes
              .map { aClass -> PsiClassReference(refExpr, aClass, STATICS_ONLY) }
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
      val modelClassResolvable = methodExpr.toModelClassResolvable() ?: return PsiReference.EMPTY_ARRAY
      val psiModelClass = modelClassResolvable.resolvedType?.unwrapped ?: return PsiReference.EMPTY_ARRAY

      val methodArgs: MutableList<PsiModelClass?> = mutableListOf()
      callExpr.expressionList?.exprList?.forEach { expr -> methodArgs.add(expr.toModelClassResolvable()?.resolvedType) }

      // First, see if we can find a name match that also matches all args
      if (!methodArgs.contains(null)) {
        @Suppress("NAME_SHADOWING") // We reframe List<PsiModelClass?> as List<PsiModelClass>
        val methodArgs = methodArgs.requireNoNulls()
        val method = psiModelClass.getMethod(
          callExpr.refExpr.id.text,
          methodArgs,
          modelClassResolvable.memberAccess,
          allowProtected = false
        )
        if (method is PsiModelMethod) {
          return arrayOf(PsiMethodReference(callExpr, method))
        }
      }

      // As a fallback, see if we can find a method by just its name
      return psiModelClass.findMethods(callExpr.refExpr.id.text, modelClassResolvable.memberAccess)
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
      val modelClassResolvable = classExpr.toModelClassResolvable() ?: return PsiReference.EMPTY_ARRAY
      val psiModelClass = modelClassResolvable.resolvedType?.unwrapped ?: return PsiReference.EMPTY_ARRAY
      return psiModelClass.findMethods(methodExpr.text, modelClassResolvable.memberAccess)
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
      val lambdaParameters = element.parent.parent ?: return arrayOf()
      val listenerMethod = lambdaParameters.references
                             .filterIsInstance<PsiMethodReference>()
                             .firstOrNull()
                             ?.resolve() as? PsiMethod
                           ?: return arrayOf()
      // Associate this expression's parameters with the listener method.
      val parameter = element as PsiDbInferredFormalParameter
      val parameterList = parameter.parent as PsiDbInferredFormalParameterList
      val index = parameterList.inferredFormalParameterList.indexOf(parameter)
      val listenerParameter = listenerMethod.parameterList.parameters.getOrNull(index) ?: return arrayOf()
      return arrayOf(PsiParameterReference(element, listenerParameter))
    }
  }

  /**
   * Provides references for [PsiDbLambdaParameters]
   *
   * From db.bnf:
   *
   * ```
   * lambdaParameters
   *   ::= '(' inferredFormalParameterList? ')'
   *        |   inferredFormalSingleParameterList
   * ```
   *
   * Example: `()` in  `() -> model.doSomething()`, `view` in `view -> model.save(view)`
   */
  private inner class LambdaParametersReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val functionalClass = getFunctionClassFromAssociatedAttribute(element) ?: return arrayOf()
      val listenerMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalClass.psiClass) ?: return arrayOf()
      return arrayOf(PsiMethodReference(element as PsiDbLambdaParameters, PsiModelMethod(functionalClass, listenerMethod)))
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
        DbTokenTypes.INTEGER_LITERAL -> PsiTypes.intType()
        DbTokenTypes.FLOAT_LITERAL -> PsiTypes.floatType()
        DbTokenTypes.LONG_LITERAL -> PsiTypes.longType()
        DbTokenTypes.DOUBLE_LITERAL -> PsiTypes.doubleType()
        DbTokenTypes.TRUE, DbTokenTypes.FALSE -> PsiTypes.booleanType()
        DbTokenTypes.NULL -> PsiTypes.nullType()
        DbTokenTypes.CHARACTER_LITERAL -> PsiTypes.charType()
        DbTokenTypes.STRING_LITERAL -> LayoutBindingTypeUtil.parsePsiType("java.lang.String", element) ?: return arrayOf()
        else -> return arrayOf()
      }
      return arrayOf(PsiLiteralReference(element, psiType))
    }
  }

  /**
   * Provides references for [PsiDbLiteralExpr]
   *
   * From db.bnf:
   * ```
   * resourcesExpr ::= RESOURCE_REFERENCE resourceParameters?
   * ```
   *
   * From _DbLexer.flex:
   * ```
   * RESOURCE_REFERENCE="@" (({IDENTIFIER} | "android") ":")? {RESOURCE_TYPE} "/" "android:"? {IDENTIFIER}
   * ```
   *
   * Example: `@string/str`, `@android:text/text1`, `string/android:id(parameters)`
   */
  private class ResourceReferenceProvider : PsiReferenceProvider() {
    companion object {
      /**
       * Maps resource type keywords from data binding expressions to their references.
       * If a keyword is not in the keys of the map, it should be mapped to itself.
       *
       * Example: `text` in `@text/zero` -> `string` in `<string name="zero">there are <b>zero</b></string>`
       */
      private val DATA_BINDING_RESOURCE_TO_XML_DECLARATION = mutableMapOf<String, String>().apply {
        put("colorStateList", "color")
        put("dimenOffset", "dimen")
        put("dimenSize", "dimen")
        put("intArray", "array")
        put("stateListAnimator", "animator")
        put("stringArray", "array")
        put("text", "string")
        put("typedArray", "array")
      }
    }

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      val facet = AndroidFacet.getInstance(element) ?: return PsiReference.EMPTY_ARRAY
      val xmlContext = element.containingFile.context?.parent as? XmlAttribute ?: return PsiReference.EMPTY_ARRAY
      val resourceReferenceText = element.firstChild.text
      val resourceValue = ResourceValue.parse(resourceReferenceText, false, true, false)
                          ?: return PsiReference.EMPTY_ARRAY

      val dataBindingResourceType = resourceValue.resourceType ?: return PsiReference.EMPTY_ARRAY

      // Change type keywords for the resource value before resolving it with [AndroidResourceToPsiResolver]
      val xmlDeclaration = DATA_BINDING_RESOURCE_TO_XML_DECLARATION[dataBindingResourceType]
      if (xmlDeclaration != null) {
        resourceValue.setResourceType(xmlDeclaration)
      }
      val resolvedResource = AndroidResourceToPsiResolver.getInstance().resolveReference(resourceValue, xmlContext, facet)
                               .getOrNull(0)?.element?.navigationElement ?: return PsiReference.EMPTY_ARRAY
      // Restore the keywords after getting the resource.
      if (xmlDeclaration != null) {
        resourceValue.setResourceType(dataBindingResourceType)
      }
      return arrayOf(PsiResourceReference(element, resolvedResource, resourceValue))
    }
  }

  /**
   * Returns the functional class if referenced by the associated attribute.
   *
   * e.g. "android:text" is resolved to java.lang.String which is not a functional class.
   *      "android:onClick" is resolved to OnClickListener which is a functional interface(class).
   */
  private fun getFunctionClassFromAssociatedAttribute(element: PsiElement): PsiModelClass? {
    val attribute = element.containingFile.context?.parent as? XmlAttribute ?: return null
    return attribute.references
      .filterIsInstance<PsiParameterReference>()
      .firstOrNull()
      ?.resolvedType
      ?.takeIf { LambdaUtil.isFunctionalType(it.type) }
  }
}
