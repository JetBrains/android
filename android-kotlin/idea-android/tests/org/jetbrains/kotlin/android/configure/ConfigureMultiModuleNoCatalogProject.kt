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
package org.jetbrains.kotlin.android.configure

import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.android.KotlinTestUtils
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunsInEdt
@RunWith(JUnit4::class)
class ConfigureMultiModuleNoCatalogProject {
    private val projectRule = AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder())
    ).initAndroid(true)


    companion object {
        private const val DEFAULT_KOTLIN_VERSION = "1.9.20"
        private const val GRADLE_CATALOG_DIR = "idea-android/testData/configuration/android-gradle/multiProjectNoCatalog"
    }

    @get:Rule
    val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

    private lateinit var buildFile: VirtualFile
    private lateinit var topBuildFile: VirtualFile

    @Test
    fun testAndroidStudioDefault() =
      doTestWithCatalog("$GRADLE_CATALOG_DIR/androidStudioDefault", "gradle")

    @Test
    fun testLibraryFile() =
      doTestWithCatalog("$GRADLE_CATALOG_DIR/libraryFile", "gradle")

    @Test
    fun testEmptyFile() =
      doTestWithCatalog("$GRADLE_CATALOG_DIR/emptyFile", "gradle")

    // Does test with module and root build files, version catalog and minimal settings file.
    private fun doTestWithCatalog(path: String,  extension: String) {
        runWriteAction {
            buildFile = projectRule.fixture.tempDirFixture.createFile("app/build.${extension}")
            topBuildFile = projectRule.fixture.tempDirFixture.createFile("build.${extension}")
            Assert.assertTrue(buildFile.isWritable)
            Assert.assertTrue(topBuildFile.isWritable)
        }
        val testRoot = TestUtils.resolveWorkspacePath("tools/adt/idea/android-kotlin").toFile()
        val file = File(testRoot, "${path}_before.$extension")
        val topFile = File(testRoot, "${path}_top_before.$extension")

        val fileText = FileUtilRt.loadFile(file, CharsetToolkit.UTF8, true)
        val topFileText = FileUtilRt.loadFile(topFile, CharsetToolkit.UTF8, true)
        runWriteAction {
            VfsUtil.saveText(buildFile, fileText)
            VfsUtil.saveText(topBuildFile, topFileText)
            VfsUtil.saveText(projectRule.fixture.tempDirFixture.createFile("settings.$extension"), "include ':app'")
        }
        val kotlinVersion = IdeKotlinVersion.get(DEFAULT_KOTLIN_VERSION)

        val project = projectRule.project
        val collector = NotificationMessageCollector.create(project)

        val configurator = KotlinAndroidGradleModuleConfigurator()
        val jvmTarget = JvmTarget.JVM_1_8.description
        val changeTracker = ChangedConfiguratorFiles()
        configurator.configureModule(projectRule.module, topBuildFile.toPsiFile(project)!!, isTopLevelProjectFile = true, kotlinVersion,
                                     jvmTarget,
                                     collector, changeTracker)
        val appModule = projectRule.project.gradleModule(":app")!!
        configurator.configureModule(appModule, buildFile.toPsiFile(project)!!, isTopLevelProjectFile = false, kotlinVersion, jvmTarget,
                                     collector, changeTracker)

        collector.showNotification()

        val afterFile = File(testRoot, "${path}_after.$extension")
        KotlinTestUtils.assertEqualsToFile(afterFile, VfsUtil.loadText(buildFile))

        val afterTopFile = File(testRoot, "${path}_top_after.$extension")
        KotlinTestUtils.assertEqualsToFile(afterTopFile, VfsUtil.loadText(topBuildFile))

        // Clear JDK table
        ProjectJdkTable.getInstance().allJdks.forEach {
            SdkConfigurationUtil.removeSdk(it)
        }
    }
}