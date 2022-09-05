/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer.getRawIcon
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.completion.DeclarationLookupObjectImpl
import org.jetbrains.kotlin.idea.completion.handlers.KotlinClassifierInsertHandler
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.source.getPsi

/**
 * This provides completions for generated [LightArgsKtClass] and [LightDirectionsKtClass] from modules with dependencies.
 *
 * This comes after [KotlinCompletionContributor]
 */
class SafeArgsKtCompletionContributor : CompletionContributor() {
  init {
    if (StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) {
      extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {

          val position = parameters.position
          val facet = position.androidFacet ?: return

          val element = position.parent as? KtSimpleNameExpression ?: return
          if (element.isImportDirectiveExpression()) return
          if (element.getReceiverExpression() != null) return

          val importedDirectives = getImportedDirectives(element)

          val lookupElements = facet.module.getDescriptorsByModulesWithDependencies().values.asSequence()
            .flatten()
            .map { ProgressManager.checkCanceled(); it }
            .filter { element.containingKtFile.packageFqName != it.fqName }
            .mapNotNull { it.getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS) { true }.firstOrNull() }
            .filterIsInstance<ClassDescriptor>()
            .filter { it.importableFqName != null }
            .filter { descriptor ->
              // Classes in imported packages are already autocompleted, and we don't want to add duplicate results.
              importedDirectives.none { importPath -> descriptor.importableFqName!!.isImported(importPath) }
            }
            .mapNotNull { createLookUpElement(it) }
            .toList()

          result.addAllElements(lookupElements)
        }

        private fun createLookUpElement(classDescriptor: ClassDescriptor): LookupElement? {
          val lookupObject = object : DeclarationLookupObjectImpl(classDescriptor) {
            override val psiElement = classDescriptor.source.getPsi()
            override fun getIcon(flags: Int) = KotlinDescriptorIconProvider.getIcon(classDescriptor, psiElement, flags)
          }

          var element = LookupElementBuilder.create(lookupObject, classDescriptor.name.asString())
            .withInsertHandler(KotlinClassifierInsertHandler)

          val classFqName = classDescriptor.fqNameSafe.takeUnless { it.isRoot } ?: return null

          val containerName = classFqName.parent()
          element = element.appendTailText(" ($containerName)", true)
          return element.withIconFromLookupObject()
        }

        private fun getImportedDirectives(element: KtSimpleNameExpression): Set<ImportPath> {
          return element.containingKtFile.importDirectives
            .mapNotNull { it.importPath }
            .toSet()
        }
      })
    }
  }
}

// Copy from BasicLookupElementFactory
private fun LookupElement.withIconFromLookupObject(): LookupElement = object : LookupElementDecorator<LookupElement>(this) {
  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.icon = getRawIcon(this@withIconFromLookupObject)
  }
}