/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.android.quickfix

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.android.DirectiveBasedActionUtils
import org.jetbrains.kotlin.android.KotlinAndroidTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File

@TestDataPath("\$PROJECT_ROOT/android-kotlin")
class FindViewByIdQuickFixTest : KotlinAndroidTestCase() {
    fun testAlreadyHasTypeArgument() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/alreadyHasTypeArgument.kt"))
    }

    fun testCastDoesNotSatisfyTypeParameterBounds() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/castDoesNotSatisfyTypeParameterBounds.kt"))
    }

    fun testFindViewWithTag() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/findViewWithTag.kt"))
    }

    fun testNotReturningTypeParameter() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/notReturningTypeParameter.kt"))
    }

    fun testNoTypeParameter() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/noTypeParameter.kt"))
    }

    fun testNullableType() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/nullableType.kt"))
    }

    fun testParenthesized() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/parenthesized.kt"))
    }

    fun testPlatformNotNullExpression() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/platformNotNullExpression.kt"))
    }

    fun testPlatformNullableExpression() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/platformNullableExpression.kt"))
    }

    fun testQualifiedRequireView() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/qualifiedRequireView.kt"))
    }

    fun testRequireView() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/requireView.kt"))
    }

    fun testRequireViewNullable() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/requireViewNullable.kt"))
    }

    fun testSafeCallOfNotNullFunction() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunction.kt"))
    }

    fun testSafeCallOfNotNullFunctionWithNonNullCast() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCallOfNotNullFunctionWithNonNullCast.kt"))
    }

    fun testSafeCast() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/safeCast.kt"))
    }

    fun testSimple() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/simple.kt"))
    }

    fun testTooManyTypeParameters() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/tooManyTypeParameters.kt"))
    }

    fun testVariableTypeAlreadyExists() {
        doTest(testFile("idea-android/testData/android/lintQuickfix/findViewById/variableTypeAlreadyExists.kt"))
    }

    private fun doTest(path: String) {
        val fileText = FileUtil.loadFile(File(testDataPath, path), true)
        val intentionText = DirectiveBasedActionUtils.findStringWithPrefixesByFrontend(fileText, "// INTENTION_TEXT: ")
                            ?: error("Empty intention text")
        val mainInspectionClassName = DirectiveBasedActionUtils.findStringWithPrefixesByFrontend(fileText, "// INSPECTION_CLASS: ")
                                      ?: error("No inspection class specified")
        val dependency = DirectiveBasedActionUtils.findStringWithPrefixesByFrontend(fileText, "// DEPENDENCY: ")
        val intentionAvailable = !DirectiveBasedActionUtils.isDirectiveDefinedForFrontend(fileText, "// INTENTION_NOT_AVAILABLE")

        val inspection = Class.forName(mainInspectionClassName).newInstance() as InspectionProfileEntry
        myFixture.enableInspections(inspection)

        val sourceFile = myFixture.copyFileToProject(path, "src/${PathUtil.getFileName(path)}")
        myFixture.configureFromExistingVirtualFile(sourceFile)

        if (dependency != null) {
            val (dependencyFile, dependencyTargetPath) = dependency.split(" -> ").map(String::trim)
            myFixture.copyFileToProject("${PathUtil.getParentPath(path)}/$dependencyFile", "src/$dependencyTargetPath")
        }

        if (intentionAvailable) {
            val oldLabel = intentionText
              .replace(": Add @SuppressLint(\"", " ")
              .replace("\") annotation", " with an annotation")
            val intention = myFixture.getAvailableIntention(intentionText)
                            ?: myFixture.getAvailableIntention(oldLabel)
                            ?: error("Failed to find intention")
            myFixture.launchAction(intention)
            if (KotlinPluginModeProvider.isK2Mode() && File(testDataPath, "$path.k2.expected").isFile) {
                myFixture.checkResultByFile("$path.k2.expected")
            }
            else {
                myFixture.checkResultByFile("$path.expected")
            }
        }
        else {
            assertNull("Intention should not be available", myFixture.availableIntentions.find { it.text == intentionText })
        }
    }

    private fun testFile(@TestDataFile testFile: String): String {
        return testFile
    }
}
