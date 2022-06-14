// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.android

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.io.File

abstract class AbstractAndroidCompletionTest : KotlinAndroidTestCase() {
    private var codeCompletionOldValue: Boolean = false
    private var smartTypeCompletionOldValue: Boolean = false

    override fun setUp() {
        super.setUp()

        val settings = CodeInsightSettings.getInstance()
        codeCompletionOldValue = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        smartTypeCompletionOldValue = settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION

        when (completionType()) {
            CompletionType.SMART -> settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false
            CompletionType.BASIC -> settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
            CompletionType.CLASS_NAME -> {}
        }
    }

    private fun completionType() = CompletionType.BASIC

    fun doTest(path: String) {
        copyResourceDirectoryForTest(path)
        val virtualFile = myFixture.copyFileToProject(path + getTestName(true) + ".kt", "src/" + getTestName(true) + ".kt")
        myFixture.configureFromExistingVirtualFile(virtualFile)
        val fileText = FileUtil.loadFile(File(testDataPath, path + getTestName(true) + ".kt"), true)
        testCompletion(fileText, JvmPlatforms.defaultJvmPlatform, { completionType, count -> myFixture.complete(completionType, count) })
    }

    override fun tearDown() {
        val settings = CodeInsightSettings.getInstance()
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = codeCompletionOldValue
        settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = smartTypeCompletionOldValue

        super.tearDown()
    }
}
