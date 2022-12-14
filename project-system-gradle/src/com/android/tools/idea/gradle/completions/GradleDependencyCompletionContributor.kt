/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.completions

import com.android.SdkConstants
import com.android.tools.idea.imports.MavenClassRegistryBase
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

private const val DEPENDENCIES = "dependencies"

private val INSIDE_BUILD_SRC_PATTERN = psiFile().with(
  object : PatternCondition<PsiFile>(null) {
    override fun accepts(psiFile: PsiFile, context: ProcessingContext?): Boolean {
      val rootProjectPath = psiFile.project.basePath ?: return false
      val vFile = psiFile.virtualFile ?: psiFile.originalFile.virtualFile ?: return false
      return vFile.path.startsWith("$rootProjectPath/buildSrc/")
    }
  }
)

private val INSIDE_DEPENDENCIES_CLOSURE_GROOVY_PATTERN = psiElement(GrClosableBlock::class.java).with(
  object : PatternCondition<GrClosableBlock>(null) {
    override fun accepts(closableBlock: GrClosableBlock, context: ProcessingContext?): Boolean {
      var preSibling: PsiElement? = closableBlock.prevSibling
      while (preSibling != null) {
        if (preSibling is GrReferenceExpression) {
          return preSibling.qualifiedReferenceName == DEPENDENCIES
        }

        preSibling = preSibling.prevSibling
      }
      return false
    }
  }
)

private val INSIDE_DEPENDENCIES_LAMBDA_KOTLIN_PATTERN = psiElement(KtLambdaArgument::class.java).with(
  object : PatternCondition<KtLambdaArgument>(null) {
    override fun accepts(lambdaArgument: KtLambdaArgument, context: ProcessingContext?): Boolean {
      var prevSibling: PsiElement? = lambdaArgument.prevSibling
      while (prevSibling != null) {
        if (prevSibling is KtNameReferenceExpression) {
          return prevSibling.getReferencedName() == DEPENDENCIES
        }

        prevSibling = prevSibling.prevSibling
      }

      return false
    }
  })

internal val INSIDE_VERSIONS_TOML_FILE = psiFile().with(
  object : PatternCondition<PsiFile>(null) {
    override fun accepts(psiFile: PsiFile, context: ProcessingContext?): Boolean {
      val vFile = psiFile.virtualFile ?: psiFile.originalFile.virtualFile ?: return false
      return vFile.name.endsWith(".versions.toml")
    }
  }
)

internal val TOML_LIBRARIES_TABLE_PATTERN = psiElement(TomlTable::class.java).with(
  object : PatternCondition<TomlTable>(null) {
    override fun accepts(tomlTable: TomlTable, context: ProcessingContext?): Boolean =
      tomlTable.header.key?.segments?.map { it.name }?.joinToString(".") == "libraries"
  }
)

private val DEPENDENCIES_IN_GRADLE_FILE_PATTERN = psiElement()
  .withLanguage(GroovyLanguage)
  .withParent(GrLiteral::class.java)
  .inFile(psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE)))
  .inside(true, INSIDE_DEPENDENCIES_CLOSURE_GROOVY_PATTERN)

private val DEPENDENCIES_IN_GRADLE_KTS_FILE_PATTERN = psiElement()
  .withLanguage(KotlinLanguage.INSTANCE)
  .withParent(KtLiteralStringTemplateEntry::class.java)
  .inFile(psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE_KTS)))
  .inside(true, INSIDE_DEPENDENCIES_LAMBDA_KOTLIN_PATTERN)

private val DEPENDENCIES_IN_BUILD_SRC_KOTLIN_PATTERN = psiElement()
  .withLanguage(KotlinLanguage.INSTANCE)
  .withParent(KtLiteralStringTemplateEntry::class.java)
  .inFile(INSIDE_BUILD_SRC_PATTERN)

private val DEPENDENCIES_IN_BUILD_SRC_JAVA_PATTERN = psiElement()
  .withLanguage(JavaLanguage.INSTANCE)
  .withParent(PsiLiteralExpression::class.java)
  .inFile(INSIDE_BUILD_SRC_PATTERN)

private val LIBRARIES_IN_VERSIONS_TOML_PATTTERN = psiElement()
  .withLanguage(TomlLanguage)
  .withParent(TomlLiteral::class.java)
  .inFile(INSIDE_VERSIONS_TOML_FILE)
  .withSuperParent(3, TOML_LIBRARIES_TABLE_PATTERN)

/**
 * Allowed pattern for providing auto-completion when managing dependencies in gradle projects.
 *
 * This can be in:
 * - a "dependencies" closure in a build.gradle or build.gradle.kts file;
 * - kotlin/java code in a "buildSrc" directory, which has to sit in the root project directory;
 * - a literal at the top level of the "libraries" table in a file whose name ends ".versions.toml".
 */
private val ALLOW_CODE_COMPLETION_PATTERN = psiElement()
  .andOr(
    DEPENDENCIES_IN_GRADLE_FILE_PATTERN,
    DEPENDENCIES_IN_GRADLE_KTS_FILE_PATTERN,
    DEPENDENCIES_IN_BUILD_SRC_KOTLIN_PATTERN,
    DEPENDENCIES_IN_BUILD_SRC_JAVA_PATTERN,
    LIBRARIES_IN_VERSIONS_TOML_PATTTERN,
  )

/**
 * Code completion for managing dependencies in gradle projects in [ALLOW_CODE_COMPLETION_PATTERN] context.
 *
 * Auto-popup is enabled when the typed string is in [ALLOW_CODE_COMPLETION_PATTERN] context.
 * @see EnableAutoPopupInStringLiteralForGradleDependencyCompletion
 */
class GradleDependencyCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      ALLOW_CODE_COMPLETION_PATTERN,
      object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          parameters.position.module ?: return

          result.addAllElements(generateLookup())
        }
      }
    )
  }

  private fun generateLookup(): Collection<LookupElement> {
    return MavenClassRegistryManager.getInstance()
      .getMavenClassRegistry()
      .getCoordinates()
      .asSequence()
      .map { ProgressManager.checkCanceled(); it }
      .map { CoordinateLookUpElement(it) }
      .toSortedSet()
  }

  private class CoordinateLookUpElement(val coordinate: MavenClassRegistryBase.Coordinate) : LookupElement(), Comparable<LookupElement> {
    override fun getLookupString(): String {
      return "${coordinate.groupId}:${coordinate.artifactId}:${coordinate.version}"
    }

    override fun compareTo(other: LookupElement): Int {
      return compareValuesBy(this, other) { it.lookupString }
    }
  }
}

/**
 *  Allow auto-popup when it's in [ALLOW_CODE_COMPLETION_PATTERN] context.
 */
class EnableAutoPopupInStringLiteralForGradleDependencyCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    if (ALLOW_CODE_COMPLETION_PATTERN.accepts(contextElement)) return ThreeState.NO

    return ThreeState.UNSURE
  }
}