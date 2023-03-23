/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.intention

import com.android.resources.ResourceType
import com.android.tools.compose.COMPOSE_STRING_RESOURCE_FQN
import com.android.tools.compose.isInsideComposableCode
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.buildResourceNameFromStringValue
import com.android.tools.idea.res.createValueResource
import com.android.tools.idea.res.getRJavaFieldName
import com.intellij.CommonBundle
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.actions.CreateXmlResourceDialog
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.android.isExtensionFunctionType
import org.jetbrains.kotlin.android.isSubclassOf
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinAndroidAddStringResource : SelfTargetingIntention<KtStringTemplateExpression>(
  KtStringTemplateExpression::class.java,
  textGetter = { AndroidBundle.message("add.string.resource.intention.text") },
  familyNameGetter = { AndroidBundle.message("add.string.resource.intention.text") }
) {
    private companion object {
        private const val CLASS_CONTEXT = "android.content.Context"
        private const val CLASS_FRAGMENT = "android.app.Fragment"
        private const val CLASS_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_CLASS_SUPPORT_FRAGMENT = "androidx.fragment.app.Fragment"
        private const val CLASS_VIEW = "android.view.View"

        private const val GET_STRING_METHOD = "getString"
        private const val EXTRACT_RESOURCE_DIALOG_TITLE = "Extract Resource"
        private const val PACKAGE_NOT_FOUND_ERROR = "package.not.found.error"
        private const val RESOURCE_DIR_ERROR = "check.resource.dir.error"
    }

    override fun startInWriteAction(): Boolean = false

    override fun isApplicableTo(element: KtStringTemplateExpression, caretOffset: Int) =
        AndroidFacet.getInstance(element.containingFile) != null

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val facet = AndroidFacet.getInstance(element.containingFile)
        requireNotNull(editor) { "This intention requires an editor." }
        checkNotNull(facet) { "This intention requires android facet." }

        val file = element.containingFile as KtFile
        val project = file.project

        val applicationPackage = getApplicationPackage(facet)
        if (applicationPackage == null) {
            Messages.showErrorDialog(project, AndroidBundle.message(PACKAGE_NOT_FOUND_ERROR), CommonBundle.getErrorTitle())
            return
        }

        val parameters = getCreateXmlResourceParameters(facet.module, element, file.virtualFile) ?: return

        runWriteAction {
            if (!createValueResource(project, parameters.resourceDirectory, parameters.name,
                                                                                   ResourceType.STRING,
                                                                                   parameters.fileName, parameters.directoryNames,
                                                                                   parameters.value)) {
                return@runWriteAction
            }

            createResourceReference(facet.module, editor, file, element, applicationPackage, parameters.name, ResourceType.STRING,
                                    parameters.placeholderExpressions)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            UndoUtil.markPsiFileForUndo(file)
            PsiManager.getInstance(project).dropResolveCaches()
        }
    }

    private fun getCreateXmlResourceParameters(module: Module, element: KtStringTemplateExpression,
                                               contextFile: VirtualFile): CreateXmlResourceParameters? {
        val (stringValue, placeholders) = buildLiteralStringAndPlaceholders(element)

        val showDialog = !ApplicationManager.getApplication().isUnitTestMode
        val resourceName = buildResourceNameFromStringValue(stringValue.withoutPlaceholders())
        val allowValueEditing = placeholders.isEmpty()

        val dialog = CreateXmlResourceDialog(
          module, ResourceType.STRING, resourceName, stringValue, true, null, contextFile, allowValueEditing)
        dialog.title = EXTRACT_RESOURCE_DIALOG_TITLE
        if (showDialog) {
            if (!dialog.showAndGet()) {
                return null
            }
        }
        else {
            dialog.close(0)
        }

        val resourceDirectory = dialog.resourceDirectory
        if (resourceDirectory == null) {
            AndroidUtils.reportError(module.project, AndroidBundle.message(RESOURCE_DIR_ERROR, module))
            return null
        }

        return CreateXmlResourceParameters(dialog.resourceName,
                                           dialog.value,
                                           dialog.fileName,
                                           resourceDirectory,
                                           dialog.dirNames,
                                           placeholders)
    }

    private fun buildLiteralStringAndPlaceholders(element: KtStringTemplateExpression): Pair<String, List<PsiElement>> {
        val placeholderExpressions: MutableList<PsiElement> = mutableListOf()
        var placeholderIndex = 1
        val literalString = buildString {
            for (child in element.children) {
                when (child) {
                    is KtEscapeStringTemplateEntry -> append(child.unescapedValue)
                    is KtLiteralStringTemplateEntry -> append(child.text)
                    is KtStringTemplateEntryWithExpression -> {
                        assert(child.children.size == 1)
                        placeholderExpressions.add(child.children[0])
                        append("%$placeholderIndex\$s")
                        placeholderIndex++
                    }

                    else -> Logger.getInstance(KotlinAndroidAddStringResource::class.java).error(
                      "Unexpected child element type: ${child::class.simpleName}")
                }
            }
        }

        return Pair(literalString, placeholderExpressions.toList())
    }

    /** Removes any placeholders of the form "%1$s" from a string. */
    private fun String.withoutPlaceholders(): String = replace(Regex("%[0-9]+\\\$s"), "")

    private fun createResourceReference(module: Module, editor: Editor, file: KtFile, element: PsiElement, aPackage: String,
                                        resName: String, resType: ResourceType, placeholderExpressions: List<PsiElement>) {
        val rFieldName = getRJavaFieldName(resName)
        val fieldName = "$aPackage.R.$resType.$rFieldName"

        val (methodCall, addContextParameter) = when {
            element.isInsideComposableCode() -> COMPOSE_STRING_RESOURCE_FQN to false
            !needContextReceiver(element) -> GET_STRING_METHOD to false
            else -> "\$context\$.$GET_STRING_METHOD" to true
        }

        val templateString = buildString {
            append("$methodCall($fieldName")
            for (i in placeholderExpressions.indices) {
                append(", \$placeholder$i\$")
            }
            append(")")
        }

        val template = TemplateImpl("", templateString, "").apply { isToReformat = true }

        if (addContextParameter) {
            val marker = MacroCallNode(VariableOfTypeMacro())
            marker.addParameter(ConstantNode(CLASS_CONTEXT))
            template.addVariable("context", marker, ConstantNode("context"), true)
        }

        placeholderExpressions.forEachIndexed { i, expr ->
            template.addVariable("placeholder$i", TextExpression(expr.text), false)
        }

        editor.caretModel.moveToOffset(element.textOffset)
        editor.document.deleteString(element.textRange.startOffset, element.textRange.endOffset)
        val marker = editor.document.createRangeMarker(element.textOffset, element.textOffset)
        marker.isGreedyToLeft = true
        marker.isGreedyToRight = true

        TemplateManager.getInstance(module.project).startTemplate(editor, template, false, null, object : TemplateEditingAdapter() {
            override fun waitingForInput(template: Template?) {
                // TODO(273768010): K2 reference shortener does not work here. Check this line later with the up-to-date KT compiler.
                ShortenReferencesFacility.getInstance().shorten(file, TextRange(marker.startOffset, marker.endOffset))
            }

            override fun beforeTemplateFinished(state: TemplateState, template: Template?) {
                // TODO(273768010): K2 reference shortener does not work here. Check this line later with the up-to-date KT compiler.
                ShortenReferencesFacility.getInstance().shorten(file, TextRange(marker.startOffset, marker.endOffset))
            }
        })
    }

    private fun needContextReceiver(element: PsiElement): Boolean {
        val classesWithGetSting = listOf(CLASS_CONTEXT, CLASS_FRAGMENT, CLASS_SUPPORT_FRAGMENT, ANDROIDX_CLASS_SUPPORT_FRAGMENT)
        val viewClass = listOf(CLASS_VIEW)
        var parent = PsiTreeUtil.findFirstParent(element, true) { it is KtClassOrObject || it is KtFunction || it is KtLambdaExpression }

        while (parent != null) {

            if (parent.isSubclassOrSubclassExtension(classesWithGetSting)) {
                return false
            }

            if (parent.isSubclassOrSubclassExtension(viewClass) ||
                (parent is KtClassOrObject && !parent.isInnerClass() && !parent.isObjectLiteral())) {
                return true
            }

            parent = PsiTreeUtil.findFirstParent(parent, true) { it is KtClassOrObject || it is KtFunction || it is KtLambdaExpression }
        }

        return true
    }

    private fun getApplicationPackage(facet: AndroidFacet) = facet.getModuleSystem().getPackageName()

    private fun PsiElement.isSubclassOrSubclassExtension(baseClasses: Collection<String>) =
            (this as? KtClassOrObject)?.isSubclassOfAny(baseClasses) ?:
            this.isSubclassExtensionOfAny(baseClasses)

    private fun PsiElement.isSubclassExtensionOfAny(baseClasses: Collection<String>) =
            (this as? KtLambdaExpression)?.isSubclassExtensionOfAny(baseClasses) ?:
            (this as? KtFunction)?.isSubclassExtensionOfAny(baseClasses) ?:
            false

    private fun KtClassOrObject.isObjectLiteral() = (this as? KtObjectDeclaration)?.isObjectLiteral() ?: false

    private fun KtClassOrObject.isInnerClass() = (this as? KtClass)?.isInner() ?: false

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun KtFunction.isSubclassExtensionOfAny(baseClasses: Collection<String>): Boolean {
        if (isK2Plugin()) {
            allowAnalysisOnEdt {
                analyze(this) {
                    val functionSymbol = this@isSubclassExtensionOfAny.getSymbol() as? KtFunctionLikeSymbol ?: return false
                    val receiverType = functionSymbol.receiverType ?: return false
                    return baseClasses.any { isSubclassOf(receiverType, it, strict = false) }
                }
            }
        }
        val descriptor = unsafeResolveToDescriptor() as FunctionDescriptor
        val extendedTypeDescriptor = descriptor.extensionReceiverParameter?.type?.constructor?.declarationDescriptor
        return extendedTypeDescriptor != null && baseClasses.any { extendedTypeDescriptor.isSubclassOf(it) }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun KtLambdaExpression.isSubclassExtensionOfAny(baseClasses: Collection<String>): Boolean {
        if (isK2Plugin()) {
            allowAnalysisOnEdt {
                analyze(this) {
                    val type = this@isSubclassExtensionOfAny.getKtType() as? KtFunctionalType ?: return false
                    if (!isExtensionFunctionType(type)) return false
                    val extendedType = type.receiverType ?: return false
                    return baseClasses.any { isSubclassOf(extendedType, it, strict = false) }
                }
            }
        }
        val bindingContext = analyze(BodyResolveMode.PARTIAL)
        val type = bindingContext.getType(this) ?: return false

        if (!type.isExtensionFunctionType) return false

        val extendedTypeDescriptor = type.arguments.first().type.constructor.declarationDescriptor ?: return false
        return baseClasses.any { extendedTypeDescriptor.isSubclassOf(it) }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun KtClassOrObject.isSubclassOfAny(baseClasses: Collection<String>): Boolean {
        if (isK2Plugin()) {
            allowAnalysisOnEdt {
                analyze(this) {
                    val classOrObjectSymbol = this@isSubclassOfAny.getClassOrObjectSymbol() ?: return false
                    return baseClasses.any { isSubclassOf(classOrObjectSymbol, it, strict = false) }
                }
            }
        }
        val declarationDescriptor = resolveToDescriptorIfAny() ?: return false
        return baseClasses.any { declarationDescriptor.isSubclassOf(it) }
    }

    private fun ClassifierDescriptor.isSubclassOf(className: String): Boolean {
        return fqNameSafe.asString() == className || isStrictSubclassOf(className)
    }

    private fun ClassifierDescriptor.isStrictSubclassOf(className: String) = defaultType.constructor.supertypes.any {
        it.constructor.declarationDescriptor?.isSubclassOf(className) ?: false
    }

    private class CreateXmlResourceParameters(val name: String,
                                              val value: String,
                                              val fileName: String,
                                              val resourceDirectory: VirtualFile,
                                              val directoryNames: List<String>,
                                              val placeholderExpressions: List<PsiElement>)
}
