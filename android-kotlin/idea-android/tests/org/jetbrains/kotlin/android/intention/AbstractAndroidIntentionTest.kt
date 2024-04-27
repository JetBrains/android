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

package org.jetbrains.kotlin.android.intention

import com.android.SdkConstants
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.android.ConfigLibraryUtil
import org.jetbrains.kotlin.android.InTextDirectivesUtils
import org.jetbrains.kotlin.android.KotlinAndroidTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File


abstract class AbstractAndroidIntentionTest : KotlinAndroidTestCase() {
    fun doTest(path: String) {
        val testFileText = FileUtil.loadFile(File(testDataPath, path))
        val intentionClassName = InTextDirectivesUtils.findStringWithPrefixes(testFileText, "// INTENTION_CLASS: ")
                                 ?: error("Intention class not found!")

        if (KotlinPluginModeProvider.isK2Mode() && InTextDirectivesUtils.isDirectiveDefined(testFileText, "// SKIP_K2")) {
            return
        }

        val notAvailable = InTextDirectivesUtils.isDirectiveDefined(testFileText, "// NOT_AVAILABLE")
        val withRuntime = InTextDirectivesUtils.isDirectiveDefined(testFileText, "// WITH_RUNTIME")
        val checkManifest = InTextDirectivesUtils.isDirectiveDefined(testFileText, "// CHECK_MANIFEST")

        try {
            val kotlinPaths = ConfigLibraryUtil.kotlinPaths
            ConfigLibraryUtil.addLibrary(myModule, "parcelizeRuntime", kotlinPaths.basePath.resolve("parcelize-runtime.jar"))
            ConfigLibraryUtil.addLibrary(myModule, "kotlinStdlib", kotlinPaths.jar(KotlinPaths.Jar.StdLib))

            if (withRuntime) {
                ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
            }

            val customManifestPath = "$path.AndroidManifest.xml"
            if (FileUtil.exists("$testDataPath/$customManifestPath")) {
                deleteManifest()
                myFixture.copyFileToProject(customManifestPath, SdkConstants.FN_ANDROID_MANIFEST_XML)
            }

            val sourceFile = myFixture.copyFileToProject(path, "src/${PathUtil.getFileName(path)}")
            myFixture.configureFromExistingVirtualFile(sourceFile)

            val intention = Class.forName(intentionClassName).newInstance() as? IntentionAction ?: error("Failed to create intention!")
            if (!intention.isAvailable(myFixture.project, myFixture.editor, myFixture.file)) {
                if (notAvailable) {
                    return
                }

                error("Intention is not available!")
            }

            if (notAvailable) {
                error("Intention should not be available!")
            }

            myFixture.launchAction(intention)

            if (checkManifest) {
                myFixture.checkResultByFile("AndroidManifest.xml", "$customManifestPath.expected", true)
            }
            else {
                myFixture.checkResultByFile("$path.expected")
            }
        }
        finally {
            ConfigLibraryUtil.removeLibrary(myModule, "parcelizeRuntime")
            ConfigLibraryUtil.removeLibrary(myModule, "kotlinStdlib")
            if (withRuntime) {
                ConfigLibraryUtil.unConfigureKotlinRuntime(myFixture.module)
            }
        }
    }
}
