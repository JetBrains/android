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

package org.jetbrains.kotlin.android.inspection

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toIoFile
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.jvm.checkers.isValidDalvikIdentifier
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION
import java.io.File

/**
 * Inspection to mark identifiers in Kotlin files as errors if that identifier is not supported by the dex format as a SimpleName.
 *
 * This should only be run on sources which will end up being run on the Android Runtime / Dalvik: build and unit test sources can use
 * all identifiers permitted by the JVM.
 */
class IllegalIdentifierInspection : AbstractKotlinInspection() {
    private class JunitPaths(val paths: List<File>, val generationId: Long) {
        companion object : Key<JunitPaths>("AndroidModuleJunitPaths")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element.node?.elementType != KtTokens.IDENTIFIER) return

                val text = element.text
                // '`' can't be escaped now
                if (!text.startsWith('`') || !text.endsWith('`')) return

                val unquotedName = KtPsiUtil.unquoteIdentifier(text)
                // This is already an error
                if (unquotedName.isEmpty()) return

                if (!isValidDalvikIdentifier(unquotedName) && checkAndroidFacet(element)) {
                    if (element.isInUnitTests() || element.isInBuildKtsFile()) {
                        return
                    }

                    val facet = AndroidFacet.getInstance(element)
                    if (facet != null && !facet.isDisposed && AndroidModuleInfo.getInstance(facet).minSdkVersion.apiLevel >= 30) {
                        // As of Android 30 this is no longer a limitation.
                        return
                    }

                    holder.registerProblem(
                        element,
                        "Identifier not allowed in Android projects",
                        ProblemHighlightType.GENERIC_ERROR,
                        RenameIdentifierFix()
                    )
                }
            }

            private fun PsiElement.isInBuildKtsFile(): Boolean {
                return language == KotlinLanguage.INSTANCE && (containingFile?.name?.endsWith(KOTLIN_DSL_SCRIPT_EXTENSION) ?: false)
            }

            private fun PsiElement.isInUnitTests(): Boolean {
                val containingFile = containingFile?.virtualFile?.let { getIoFile(it) }
                val module = AndroidPsiUtils.getModuleSafely(this)

                if (module != null && containingFile != null) {
                    val currentGenerationId = ProjectRootModificationTracker.getInstance(module.project).modificationCount
                    val junitTestPaths = module.getUserData(JunitPaths)
                        ?.takeIf { it.generationId == currentGenerationId }
                            ?: JunitPaths(getJunitTestPaths(module), currentGenerationId).also { module.putUserData(JunitPaths, it) }

                    if (junitTestPaths.paths.any { containingFile.startsWith(it) }) {
                        return true
                    }
                }

                return false
            }

            private fun checkAndroidFacet(element: PsiElement): Boolean {
                return element.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode
            }
        }
    }

    private fun getJunitTestPaths(module: Module): List<File> {
        val androidFacet = AndroidFacet.getInstance(module) ?: return emptyList()
        val unitTestSources = SourceProviders.getInstance(androidFacet).unitTestSources
        return (unitTestSources.javaDirectories + unitTestSources.kotlinDirectories).map { it.toIoFile() }
    }

    private fun getIoFile(virtualFile: VirtualFile): File? {
        var path = virtualFile.path

        // Taken from LocalFileSystemBase.convertToIOFile
        if (path.length == 2 && SystemInfo.isWindows && OSAgnosticPathUtil.startsWithWindowsDrive(path)) {
            path += "/"
        }

        return File(path).takeIf { it.exists() }
    }
}
