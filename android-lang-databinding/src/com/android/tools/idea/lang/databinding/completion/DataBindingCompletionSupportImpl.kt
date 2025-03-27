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

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.lang.databinding.DataBindingCompletionSupport
import com.android.tools.idea.lang.databinding.config.DbFileType
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import org.jetbrains.android.dom.layout.Import
import org.jetbrains.android.dom.layout.Layout
import org.jetbrains.annotations.Contract

/**
 * Used to suggest completions related to data-binding. This is used in the expressions (`@{...}`) and in
 * `<variable type='...'>` and `<import type='...'>` tags.
 */
class DataBindingCompletionSupportImpl : DataBindingCompletionSupport {
  companion object {
    /**
     * Finds the relevant package prefix given the current offset position.
     *
     * "abc.def.ghi.|" -> "abc.def.ghi"
     * "   abc.def.ghi.|" -> "abc.def.ghi"
     * "abc.def.ghi.Cl|ass" -> "abc.def.ghi"
     * "abc.de|f.ghi" -> "abc"
     * "ab|c.def.ghi" -> ""
     */
    private fun getPackagePrefix(context: PsiElement, offset: Int): String {
      return getPackagePrefix(context.containingFile.viewProvider.contents, offset)
    }

    /**
     * Note: Originally copied from [AllClassesGetter] because it was private.
     * (The method has since been moved to [LimitedAccessibleClassPreprocessor.getPackagePrefix].)
     */
    @VisibleForTesting
    fun getPackagePrefix(text: CharSequence, offset: Int): String {
      var startIndex = offset
      while (startIndex > 0 && text[startIndex - 1].let { c -> Character.isJavaIdentifierPart(c) || c == '.' }) {
        startIndex--
      }
      val startToOffset = text.subSequence(startIndex, offset).toString()
      val lastPeriodIndex = startToOffset.lastIndexOf('.')
      return if (lastPeriodIndex > 0) startToOffset.substring(0, lastPeriodIndex) else ""
    }
  }

  override fun addCompletions(params: CompletionParameters, resultSet: CompletionResultSet) {
    val originalPosition = params.originalPosition
    val originalParent = originalPosition?.parent ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(originalParent) ?: return
    getRealContainingFile(originalParent.containingFile) ?: return

    val packagePrefix = getPackagePrefix(originalParent, params.offset)
    fillAliases(resultSet, packagePrefix, originalPosition, module, originalParent)
    fillClassNames(resultSet, packagePrefix, module)
  }

  /**
   * Adds completion suggestions for classes included via `<import>`s.
   *
   * @param resultSet the set to add the suggestions to.
   */
  private fun fillAliases(resultSet: CompletionResultSet,
                          packagePrefix: String,
                          originalPosition: PsiElement,
                          module: Module,
                          originalParent: PsiElement) {
    val containingFile = getRealContainingFile(originalParent.containingFile)
    if (containingFile !is XmlFile) return

    val project = module.project
    val domManager = DomManager.getDomManager(project)

    val tag = PsiTreeUtil.getParentOfType(originalPosition, XmlTag::class.java, false, PsiFile::class.java)
    if (domManager.getDomElement(tag) is Import) return // <import> tags generate aliases, they don't read from them

    val file = domManager.getFileElement(containingFile, Layout::class.java) ?: return
    val facade = JavaPsiFacade.getInstance(project)

    val moduleScope = module.getModuleSystem().getResolveScope(ScopeType.MAIN)
    for (data in file.rootElement.dataElements) {
      if (packagePrefix.isEmpty()) {
        // If here, we're trying to autocomplete an unqualified name, e.g. "" or "Lis". Include all
        // import aliases directly as possible auto-completions.
        for (import in data.imports) {
          val alias = DataBindingUtil.getAlias(import) ?: continue

          val type = import.type.xmlAttributeValue ?: continue
          val typeValue = type.value.replace('$', '.')
          val aClass = facade.findClass(typeValue, moduleScope)
          if (aClass != null) {
            resultSet.addElement(getClassReferenceElement(alias, aClass))
          }
        }
      }
      else {
        // If here, we're trying to autocomplete in the context of a package, e.g. "java.util." or "java.util.Lis"
        // At this point, most imports (which provide a single-name alias) don't apply, unless we
        // happen to be trying to autocomplete an inner class, e.g. "Map.Entr", which produces a packagePrefix "Map"
        // At that point, try replacing the first part of the prefix with the fully qualified version
        // from a matching import.
        val possibleAlias = packagePrefix.substringBefore('.')
        val import = data.imports.firstOrNull { import -> DataBindingUtil.getAlias(import) == possibleAlias } ?: continue
        val type = import.type.stringValue ?: continue
        val alias = DataBindingUtil.getAlias(import)!! // Non-null because we matched firstOrNull condition above

        // The following line converts the alias into its type
        // <import type="java.util.Map" alias="MyMap">
        // <variable type="MyMap.Entry">
        // fqcn <- "java.util.Map.Entry"
        val fqcn = packagePrefix.replaceFirst(alias, type)
        val aClass = facade.findClass(fqcn, moduleScope) ?: continue
        aClass.innerClasses.asSequence()
          .filter { innerClass -> innerClass.name != null }
          .forEach { innerClass -> resultSet.addElement(getClassReferenceElement(innerClass.name!!, innerClass)) }
      }
    }
  }

  private fun fillClassNames(resultSet: CompletionResultSet,
                             packagePrefix: String,
                             module: Module) {
    val project = module.project
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val rootPackage = javaPsiFacade.findPackage(packagePrefix)
    val moduleScope = module.getModuleSystem().getResolveScope(ScopeType.MAIN)
    if (rootPackage == null) {
      // If here, it's because we are specifying the path to an inner class, e.g. "a.b.c.Outer.Inner"
      // "a.b.c.Outer" is treated as an invalid package (thus, rootPackage is null), but that means we
      // should treat it as a fully qualified class name.
      val outerClass = javaPsiFacade.findClass(packagePrefix, moduleScope)
      if (outerClass != null) {
        for (innerClass in outerClass.innerClasses) {
          resultSet.addElement(JavaPsiClassReferenceElement(innerClass))
        }
      }
    }
    else {
      // If here, we are grounded to some package (or maybe no package, i.e. top-level).
      // Either way, add all direct subpackages.
      rootPackage.getSubPackages(moduleScope).asSequence()
        // Make sure that the package contains some useful content before suggesting it. Without this check,
        // many res folders also show up as package suggestions - eg. drawable-hdpi, which is clearly not a package.
        .filter { pkg -> pkg.getSubPackages(moduleScope).isNotEmpty() || pkg.getClasses(moduleScope).isNotEmpty() }
        // pkg.name is always non-null for subpackages
        .filter { pkg -> pkg.name!!.all { char -> Character.isJavaIdentifierPart(char) } }
        .forEach { pkg ->
          resultSet.addElement(LookupElementBuilder.createWithIcon(pkg).withTypeDecorator(TailTypes.dotType()))
        }

      if (rootPackage.name.isNullOrEmpty()) {
        // If here, we're typing an unqualified name (e.g. "AtomicBo|"). At this point, add all
        // classes accessible to the current module, as the user might not remember the class's
        // package.
        val query = AllClassesSearch.search(moduleScope, project)
        query.findAll().forEach { psiClass -> resultSet.addElement(JavaPsiClassReferenceElement(psiClass)) }
      }
      else {
        // If in a subpackage (e.g. "a.b.c"), only add classes directly under that package.
        for (psiClass in rootPackage.getClasses(moduleScope)) {
          resultSet.addElement(JavaPsiClassReferenceElement(psiClass))
        }
      }
    }
  }

  /**
   * In case of editing the injected language fragment (alt+enter -> Edit AndroidDataBinding Fragment), the top level file isn't the file
   * where the code is actually located.
   */
  @Contract("!null -> !null; null -> null")
  private fun getRealContainingFile(file: PsiFile?): PsiFile? {
    if (file?.fileElementType == DbFileType.INSTANCE) {
      return file?.context?.containingFile
    }
    return file
  }

  private fun getClassReferenceElement(alias: String, referenceClass: PsiClass): JavaPsiClassReferenceElement {
    val element = JavaPsiClassReferenceElement(referenceClass)
    element.forcedPresentableName = alias
    element.setInsertHandler { _, _ -> } // Override the default InsertHandler to prevent adding the FQCN.
    return element
  }
}