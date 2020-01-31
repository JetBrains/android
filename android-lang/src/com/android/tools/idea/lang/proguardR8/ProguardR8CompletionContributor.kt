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

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiFile
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.not
import com.intellij.patterns.StandardPatterns.or
import com.intellij.patterns.StandardPatterns.string
import com.intellij.util.ProcessingContext

/**
 * Provides code completion for key words for proguardR8 files
 *
 * Provides code completion for flags, java keywords and proguardR8 specific wildcards
 */
class ProguardR8CompletionContributor : CompletionContributor() {
  companion object {
    val VALID_FLAGS = setOf(
      "adaptclassstrings",
      "adaptresourcefilecontents",
      "adaptresourcefilenames",
      "addconfigurationdebugging",
      "allowaccessmodification",
      "alwaysinline",
      "applymapping",
      "assumenosideeffects",
      "assumevalues",
      "basedirectory",
      "checkdiscard",
      "classobfuscationdictionary",
      "dontnote",
      "dontobfuscate",
      "dontoptimize",
      "dontshrink",
      "dontusemixedcaseclassnames",
      "dontwarn",
      "flattenpackagehierarchy",
      "identifiernamestring",
      "if",
      "ignorewarnings",
      "include",
      "injars",
      "keep",
      "keepattributes",
      "keepdirectories",
      "keeppackagenames",
      "keepparameternames",
      "libraryjars",
      "obfuscationdictionary",
      "optimizationpasses",
      "overloadaggressively",
      "packageobfuscationdictionary",
      "printconfiguration",
      "printmapping",
      "printseeds",
      "printusage",
      "renamesourcefileattribute",
      "repackageclasses",
      "verbose",
      "whyareyoukeeping"
    )

    private val FIELD_METHOD_MODIFIERS = setOf(
      "abstract",
      "final",
      "native",
      "private",
      "protected",
      "public",
      "static",
      "strictfp",
      "synchronized",
      "transient",
      "volatile"
    )

    private val FIELD_METHOD_WILDCARDS = mapOf(
      "<clinit>" to "matches all static initializers",
      "<fields>" to "matches all fields",
      "<init>" to "matches all constructors",
      "<methods>" to "matches all methods"
    )

    private val CLASS_TYPE = setOf(
      "class",
      "interface",
      "enum"
    )

    private val flagCompletionProvider = object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        resultSet: CompletionResultSet
      ) {
        resultSet.addAllElements(VALID_FLAGS.map { LookupElementBuilder.create(it) })
      }
    }

    private val methodModifierCompletionProvider = object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        resultSet: CompletionResultSet
      ) {
        resultSet.addAllElements(FIELD_METHOD_MODIFIERS.map { LookupElementBuilder.create(it) })
      }
    }

    private val fieldsAndMethodsWildcardsCompletionProvider = object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        resultSet: CompletionResultSet
      ) {
        resultSet
          .withPrefixMatcher("<")
          .addAllElements(FIELD_METHOD_WILDCARDS.map { LookupElementBuilder.create(it.key).withTailText(" " + it.value) })
      }
    }

    private val classTypeCompletionProvider = object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        processingContext: ProcessingContext,
        resultSet: CompletionResultSet
      ) {
        resultSet.addAllElements(CLASS_TYPE.map { LookupElementBuilder.create(it) })
      }
    }

    private val anyProguardElement = psiElement().withLanguage(ProguardR8Language.INSTANCE)

    private val insideClassSpecification = psiElement().inside(psiElement(ProguardR8PsiTypes.CLASS_SPECIFICATION_BODY))

    private val insideTypeList = anyProguardElement.inside(psiElement(ProguardR8PsiTypes.TYPE_LIST))
  }

  init {
    // Add autocompletion for "flag names".
    extend(
      CompletionType.BASIC,
      psiElement(ProguardR8PsiTypes.FLAG),
      flagCompletionProvider
    )

    // Add autocompletion for java key words ("private", "public" ...) inside class specification body
    extend(
      CompletionType.BASIC,
      and(
        insideClassSpecification,
        not(insideTypeList),
        psiElement().afterLeaf(
          or(
            psiElement(ProguardR8PsiTypes.SEMICOLON),
            psiElement(ProguardR8PsiTypes.OPEN_BRACE),
            psiElement().withText(string().oneOf(FIELD_METHOD_MODIFIERS))
          )
        )
      ),
      methodModifierCompletionProvider
    )

    // Add autocompletion for keywords like <methods> <fields> <init>.
    extend(
      CompletionType.BASIC,
      and(
        insideClassSpecification,
        not(insideTypeList),
        anyProguardElement.afterLeaf(psiElement().withText("<"))
      ),
      fieldsAndMethodsWildcardsCompletionProvider
    )

    // Add autocompletion for CLASS_TYPE keywords in class specification header.
    extend(
      CompletionType.BASIC,
      and(
        anyProguardElement.afterLeaf(
          or(
            psiElement(ProguardR8PsiTypes.FLAG).withText(string().contains("keep")),
            psiElement(ProguardR8PsiTypes.FLAG).withText(string().contains("if"))
          )
        )
      ),
      classTypeCompletionProvider
    )
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is ProguardR8PsiFile) {
      // We need lower case dummy because original ("IntellijIdeaRulezzz") breaks lexer for flags (flags can be only lowercase).
      context.dummyIdentifier = "lowercasedummy"
    }
    super.beforeCompletion(context)
  }
}
