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
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.android.KotlinTestUtils.assertEqualsToFile
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunsInEdt
@RunWith(JUnit4::class)
class ConfigureCatalogSingleModuleProjectTest {

    private val projectRule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder())
    companion object {
        private val DEFAULT_VERSION = TestUtils.KOTLIN_VERSION_FOR_TESTS
        private const val GRADLE_CATALOG_DIR = "idea-android/testData/configuration/android-gradle/catalog"
    }

    @get:Rule
    val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

    private lateinit var buildFile: VirtualFile
    private lateinit var catalogFile: VirtualFile

    @Test
    fun testSingleModuleProject() =
      doTestWithSingleModule("${GRADLE_CATALOG_DIR}/singleModule", "gradle")

    private fun doTestWithSingleModule(path: String,  extension: String) {
        runWriteAction {
            buildFile = projectRule.fixture.tempDirFixture.createFile("build.${extension}")
            catalogFile = projectRule.fixture.tempDirFixture.createFile("gradle/libs.versions.toml")
            Assert.assertTrue(buildFile.isWritable)
            Assert.assertTrue(catalogFile.isWritable)
        }
        val testRoot = TestUtils.resolveWorkspacePath("tools/adt/idea/android-kotlin").toFile()
        val file = File(testRoot, "${path}_before.$extension")
        val versionCatalogFile = File(testRoot, "${path}_before.versions.toml")

        val fileText = FileUtilRt.loadFile(file, CharsetToolkit.UTF8, true)
        val catalogText = FileUtilRt.loadFile(versionCatalogFile, CharsetToolkit.UTF8, true)
        runWriteAction {
            VfsUtil.saveText(buildFile, fileText)
            VfsUtil.saveText(catalogFile, catalogText)
        }
        val version = IdeKotlinVersion.get(DEFAULT_VERSION)

        val project = projectRule.project
        val collector = NotificationMessageCollector.create(project)

        val configurator = KotlinAndroidGradleModuleConfigurator()
        val jvmTarget = JvmTarget.JVM_1_8.description
        val changedFiles = ChangedConfiguratorFiles()
        configurator.configureModule(projectRule.module, buildFile.toPsiFile(project)!!, isTopLevelProjectFile = true, version, jvmTarget,
                                     collector, changedFiles)
        configurator.configureModule(projectRule.module, buildFile.toPsiFile(project)!!, isTopLevelProjectFile = false, version, jvmTarget,
                                     collector, changedFiles)

        collector.showNotification()

        val afterFile = File(testRoot, "${path}_after.$extension")
        assertEqualsToFile(afterFile, VfsUtil.loadText(buildFile))

        val afterCatalogFile = File(testRoot, "${path}_after.versions.toml")
        assertEqualsToFile(afterCatalogFile, VfsUtil.loadText(catalogFile)) {
            it.replace("\$VERSION$", DEFAULT_VERSION)
        }

        // Clear JDK table
        ProjectJdkTable.getInstance().allJdks.forEach {
            SdkConfigurationUtil.removeSdk(it)
        }
    }
}