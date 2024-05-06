/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.quickfix

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.android.DirectiveBasedActionUtils.findStringWithPrefixesByFrontend
import org.jetbrains.kotlin.android.DirectiveBasedActionUtils.isDirectiveDefinedForFrontend
import org.jetbrains.kotlin.android.KotlinAndroidTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File


abstract class AbstractAndroidLintQuickfixTest : KotlinAndroidTestCase() {

    fun doTest(path: String) {
        val fileText = FileUtil.loadFile(File(testDataPath, path), true)
        val intentionText =
            findStringWithPrefixesByFrontend(fileText, "// INTENTION_TEXT: ") ?: error("Empty intention text")
        val mainInspectionClassName =
            findStringWithPrefixesByFrontend(fileText, "// INSPECTION_CLASS: ") ?: error("No inspection class specified")
        val dependency = findStringWithPrefixesByFrontend(fileText, "// DEPENDENCY: ")
        val intentionAvailable = !isDirectiveDefinedForFrontend(fileText, "// INTENTION_NOT_AVAILABLE")

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

}
