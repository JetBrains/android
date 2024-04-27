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

package org.jetbrains.kotlin.android.intention

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.compose.COMPOSE_STRING_RESOURCE_FQN
import com.android.tools.compose.isInsideComposableCode
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.buildResourceNameFromStringValue
import com.android.tools.idea.res.createValueResource
import com.android.tools.idea.res.getRJavaFieldName
import com.intellij.CommonBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.android.actions.CreateXmlResourceDialog
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.plugin.suppressAndroidPlugin
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

abstract class KotlinAndroidAddStringResourceIntentionBase : SelfTargetingIntention<KtStringTemplateExpression>(
  KtStringTemplateExpression::class.java,
  textGetter = { AndroidBundle.message("add.string.resource.intention.text") },
  familyNameGetter = { AndroidBundle.message("add.string.resource.intention.text") }
) {
    private companion object {
        private val CLASS_IDS_WITH_GET_STRING = setOf(
          ClassId.fromString("android/content/Context"),
          ClassId.fromString("android/app/Fragment"),
          ClassId.fromString("android/support/v4/app/Fragment"),
          ClassId.fromString("androidx/fragment/app/Fragment"),
          ClassId.fromString("android/content/res/Resources"),
        )
        private val CLASS_IDS_REQUIRING_CONTEXT_PARAMETER = setOf(
          ClassId.fromString("android/view/View")
        )

        private const val GET_STRING_METHOD = "getString"
        private const val EXTRACT_RESOURCE_DIALOG_TITLE = "Extract Resource"
        private const val PACKAGE_NOT_FOUND_ERROR = "package.not.found.error"
        private const val RESOURCE_DIR_ERROR = "check.resource.dir.error"
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        // Disable intention preview because the fix goes in a separate file.
        return IntentionPreviewInfo.EMPTY
    }

    override fun checkFile(file: PsiFile): Boolean {
        return true
    }

    override fun isApplicableTo(element: KtStringTemplateExpression, caretOffset: Int) =
        !suppressAndroidPlugin() &&
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

                    else -> Logger.getInstance(KotlinAndroidAddStringResourceIntentionBase::class.java).error(
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
            marker.addParameter(ConstantNode(SdkConstants.CLASS_CONTEXT))
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
        var parent = element.parentOfTypes(KtClassOrObject::class, KtFunction::class, KtLambdaExpression::class)

        while (parent != null) {

            if (parent.hasDispatchReceiverOfAnyOfTypes(CLASS_IDS_WITH_GET_STRING)) {
                return false
            }

            if (parent.hasDispatchReceiverOfAnyOfTypes(CLASS_IDS_REQUIRING_CONTEXT_PARAMETER) ||
                (parent is KtClassOrObject && !parent.isInnerClass() && !parent.isObjectLiteral())) {
                return true
            }

            parent = parent.parentOfTypes(KtClassOrObject::class, KtFunction::class, KtLambdaExpression::class)
        }

        return true
    }

    private fun getApplicationPackage(facet: AndroidFacet) = facet.getModuleSystem().getPackageName()

    private fun KtElement.hasDispatchReceiverOfAnyOfTypes(classIds: Set<ClassId>) = when (this) {
        is KtClassOrObject -> isSubclassOfAnyOf(classIds)
        is KtLambdaExpression -> isReceiverSubclassOfAnyOf(classIds)
        is KtFunction -> isReceiverSubclassOfAnyOf(classIds)
        else -> false
    }

    private fun KtClassOrObject.isObjectLiteral() = this is KtObjectDeclaration && isObjectLiteral()

    private fun KtClassOrObject.isInnerClass() = this is KtClass && isInner()

    abstract fun KtFunction.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean

    abstract fun KtLambdaExpression.isReceiverSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean

    abstract fun KtClassOrObject.isSubclassOfAnyOf(baseClassIds: Collection<ClassId>): Boolean

    private class CreateXmlResourceParameters(val name: String,
                                              val value: String,
                                              val fileName: String,
                                              val resourceDirectory: VirtualFile,
                                              val directoryNames: List<String>,
                                              val placeholderExpressions: List<PsiElement>)
}
