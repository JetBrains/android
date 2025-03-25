/*
 * Copyright (C) 2023 The Android Open Source Project
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

package org.jetbrains.kotlin.android.folding

import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.R_CLASS
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.getConfiguredValue
import com.android.resources.ResourceType
import com.android.tools.idea.folding.AndroidFoldingSettings
import com.android.tools.idea.kotlin.tryEvaluateConstantAsText
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.util.parentOfType
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.util.regex.Pattern


class ResourceFoldingBuilder : FoldingBuilderEx() {

    companion object {
        // See lint's StringFormatDetector
        private val FORMAT = Pattern.compile("%(\\d+\\$)?([-+#, 0(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])")
        private const val FOLD_MAX_LENGTH = 60
        private val UNIT_TEST_MODE: Boolean = ApplicationManager.getApplication().isUnitTestMode
        private val RESOURCE_TYPES = listOf(ResourceType.STRING,
                                            ResourceType.DIMEN,
                                            ResourceType.INTEGER,
                                            ResourceType.PLURALS)
    }

    private val isFoldingEnabled = AndroidFoldingSettings.getInstance().isCollapseAndroidStrings

    override fun getPlaceholderText(node: ASTNode): String? {

        tailrec fun KtElement.unwrapReferenceAndGetValue(
          ktCallElement: KtCallElement? = null
        ): String? = when (this) {
            is KtDotQualifiedExpression ->
                selectorExpression?.unwrapReferenceAndGetValue(ktCallElement)
            is KtCallElement ->
                valueArguments.firstOrNull()?.getArgumentExpression()?.unwrapReferenceAndGetValue(this)
            else ->
                (this as? KtReferenceExpression)?.getAndroidResourceValue(ktCallElement)
        }

        val element = SourceTreeToPsiMap.treeElementToPsi(node) as? KtElement ?: return null
        return element.unwrapReferenceAndGetValue()
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is KtFile ||
            root.isScript() ||
            quick && !UNIT_TEST_MODE ||
            !isFoldingEnabled ||
            AndroidFacet.getInstance(root) == null
        ) {
            return emptyArray()
        }

        val result = arrayListOf<FoldingDescriptor>()
        root.accept(
          object : KtTreeVisitorVoid() {
              override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                  expression.getFoldingDescriptor()?.let { result.add(it) }
                  super.visitSimpleNameExpression(expression)
              }
          }
        )

        return result.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = isFoldingEnabled

    private fun KtReferenceExpression.getFoldingDescriptor(): FoldingDescriptor? {
        // TODO(b/268387878)
        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(this) {
                val symbol = mainReference.resolveToSymbol() ?: return null
                val resourceType = getAndroidResourceType(symbol) ?: return null
                if (resourceType !in RESOURCE_TYPES) return null
            }
        }

        fun PsiElement.createFoldingDescriptor(): FoldingDescriptor {
            val dependencies: Set<PsiElement> = setOf(this)
            return FoldingDescriptor(node, textRange, null, dependencies)
        }

        val element = parent as? KtDotQualifiedExpression ?: this
        val getResourceValueCall = element.parentOfType<KtCallElement>()?.takeIf { it.isFoldableGetResourceValueCall() }
        if (getResourceValueCall != null) {
            val qualifiedCall = getResourceValueCall.parent as? KtDotQualifiedExpression
            if (qualifiedCall?.selectorExpression == getResourceValueCall) {
                return qualifiedCall.createFoldingDescriptor()
            }

            return getResourceValueCall.createFoldingDescriptor()
        }

        return element.createFoldingDescriptor()
    }

    private fun KtCallElement.isFoldableGetResourceValueCall(): Boolean {
        return checkMethodName { methodName ->
            methodName == "getString" ||
            methodName == "getText" ||
            methodName == "getInteger" ||
            methodName.startsWith("getDimension") ||
            methodName.startsWith("getQuantityString")
        }
    }

    private fun KtCallElement.checkMethodName(predicate: (String) -> Boolean): Boolean {
        analyze(this) {
            val symbol = resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return false
            val methodName = symbol.callableId?.callableName?.identifier ?: return false
            return predicate.invoke(methodName)
        }
    }

    private fun KaSession.getAndroidResourceType(symbol: KaSymbol): ResourceType? {
        val elementType = symbol.containingDeclaration as? KaClassSymbol ?: return null
        val classId = elementType.classId ?: return null
        val outerClassId = classId.outerClassId ?: return null
        if (R_CLASS != outerClassId.shortClassName.asString()) return null
        if (outerClassId.asFqNameString() != "$ANDROID_PKG.$R_CLASS") {
            return ResourceType.fromClassName(classId.shortClassName.identifier)
        }

        return null
    }

    private fun KtReferenceExpression.getAndroidResourceValue(
      call: KtCallElement? = null
    ): String? {
        lateinit var resourceModule: Module
        lateinit var resourceType: ResourceType
        var resolvedName: String? = null

        // TODO(b/268387878)
        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(this) {
                val symbol = mainReference.resolveToSymbol() ?: return null
                resourceType = getAndroidResourceType(symbol) ?: return null
                resourceModule = mainReference.resolve()?.module ?: return null
                resolvedName = symbol.name?.identifier
            }
        }

        val resources = StudioResourceRepositoryManager.getInstance(resourceModule)?.appResources ?: return null

        val referenceConfig = FolderConfiguration().apply { localeQualifier = LocaleQualifier("xx") }
        val key = resolvedName ?: return null
        val resourceValue = resources.getResourceValue(resourceType, key, referenceConfig) ?: return null
        val text = if (call != null) formatArguments(call, resourceValue) else resourceValue

        if (resourceType == ResourceType.STRING || resourceType == ResourceType.PLURALS) {
            return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"'
        }
        else if (resourceType == ResourceType.INTEGER || text.length <= 1) {
            // Don't just inline empty or one-character replacements: they can't be expanded by a mouse click
            // so are hard to use without knowing about the folding keyboard shortcut to toggle folding.
            // This is similar to how IntelliJ 14 handles call parameters
            // Integer resources have better context when the resource key is still included, similar to parameter hints.
            return "$key: $text"
        }

        return StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH, 0)
    }

    private tailrec fun ResourceRepository.getResourceValue(
      type: ResourceType,
      name: String,
      referenceConfig: FolderConfiguration,
      processedValues: MutableSet<String>? = null
    ): String? {
        val value = getConfiguredValue(type, name, referenceConfig)?.value ?: return null
        if (!value.startsWith('@')) {
            return value
        }

        val (referencedTypeName, referencedName) = value.substring(1).split('/').takeIf { it.size == 2 } ?: return value

        // If this reference is to a value that's already been seen, there's a cycle of references that will never resolve.
        val processedValueSet = processedValues ?: mutableSetOf()
        if (!processedValueSet.add(value)) return value

        val referencedType = ResourceType.fromXmlValue(referencedTypeName) ?: return value
        return getResourceValue(referencedType, referencedName, referenceConfig, processedValueSet)
    }

    // Converted from com.android.tools.idea.folding.InlinedResource#insertArguments
    private fun formatArguments(callExpression: KtCallElement, formatString: String): String {
        if (!formatString.contains('%')) {
            return formatString
        }

        var args = callExpression.valueArguments
        if (args.isEmpty() || args.first().getArgumentExpression()?.isValid == false) {
            return formatString
        }

        val isGetQuantityString = callExpression.checkMethodName { it == "getQuantityString" }
        if (args.size >= 3 && isGetQuantityString) {
            // There are two versions:
            // String getQuantityString (int id, int quantity)
            // String getQuantityString (int id, int quantity, Object... formatArgs)
            // In the second version formatArgs references (1$, 2$, etc) are "one off" (ie args[1] is "quantity" instead of formatArgs[0])
            // Ignore "quantity" argument for plurals since it's not used for formatting
            args = args.subList(1, args.size)
        }

        val matcher = FORMAT.matcher(formatString)
        var index = 0
        var prevIndex = 0
        var nextNumber = 1
        var start = 0
        val sb = StringBuilder(2 * formatString.length)
        while (true) {
            if (matcher.find(index)) {
                if ("%" == matcher.group(6)) {
                    index = matcher.end()
                    continue
                }
                val matchStart = matcher.start()
                // Make sure this is not an escaped '%'
                while (prevIndex < matchStart) {
                    val c = formatString[prevIndex]
                    if (c == '\\') {
                        prevIndex++
                    }
                    prevIndex++
                }
                if (prevIndex > matchStart) {
                    // We're in an escape, ignore this result
                    index = prevIndex
                    continue
                }

                index = matcher.end()

                // Shouldn't throw a number format exception since we've already
                // matched the pattern in the regexp
                val number: Int
                var numberString: String? = matcher.group(1)
                if (numberString != null) {
                    // Strip off trailing $
                    numberString = numberString.substring(0, numberString.length - 1)
                    number = Integer.parseInt(numberString)
                    nextNumber = number + 1
                }
                else {
                    number = nextNumber++
                }

                if (number > 0 && number < args.size) {
                    val argExpression = args[number].getArgumentExpression()
                    val value = argExpression?.tryEvaluateConstantAsText() ?: argExpression?.text

                    for (i in start until matchStart) {
                        sb.append(formatString[i])
                    }

                    sb.append('{')
                    sb.append(value)
                    sb.append('}')
                    start = index
                }
            }
            else {
                var i = start
                val n = formatString.length
                while (i < n) {
                    sb.append(formatString[i])
                    i++
                }
                break
            }
        }

        return sb.toString()
    }
}
