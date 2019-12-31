/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.sdklib.AndroidVersion.VersionCodes.LOLLIPOP
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.templates.TemplateAttributes.ATTR_JAVA_VERSION
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.android.AndroidTestBase
import java.io.File

/**
 * Template test special cases.
 */
class FrameworkTemplateTest : TemplateTestBase() {
  fun testJdk7() {
    if (DISABLED) {
      return
    }
    val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()!!
    if (!IdeSdks.getInstance().isJdk7Supported(sdkData)) {
      println("JDK 7 not supported by current SDK manager: not testing")
      return
    }
    val target = sdkData.targets.last()
    val state = createNewProjectState(sdkData, defaultModuleTemplate)

    ensureSdkManagerAvailable()
    // TODO: Allow null activity state!
    val activity = findTemplate("activities", "BasicActivity")
    state.activityTemplateState.setTemplateLocation(activity)
    state.activityTemplateState.put(ATTR_JAVA_VERSION, "1.7")
    checkApiTarget(LOLLIPOP, target.version.apiLevel, state, "Test17", TestTemplateWizardState())
  }

  fun testTemplateFormatting() {
    setUpFixture()
    val template = Template.createFromPath(getTestTemplatePath())
    val context = createRenderingContext(template, myFixture.project, File(myFixture.tempDirPath), File("dummy"))
    template.render(context, false)
    FileDocumentManager.getInstance().saveAllDocuments()
    val fileSystem = LocalFileSystem.getInstance()
    val desired = fileSystem.findFileByIoFile(File(getTestTemplatePath(), "MergedStringsFile.xml"))!!
    val actual = fileSystem.findFileByIoFile(File(myFixture.tempDirPath, FileUtil.join("values", "TestTargetResourceFile.xml")))!!
    desired.refresh(false, false)
    actual.refresh(false, false)
    PlatformTestUtil.assertFilesEqual(desired, actual)
  }

  fun testRelatedParameters() {
    val template = Template.createFromPath(getTestTemplatePath())
    val templateMetadata = template.metadata!!

    val activityTitle = templateMetadata.getParameter("activityTitle")
    val layoutName = templateMetadata.getParameter("layoutName")
    val activityClass = templateMetadata.getParameter("activityClass")
    val mainFragment = templateMetadata.getParameter("mainFragment")
    val detailsActivity = templateMetadata.getParameter("detailsActivity")
    val detailsLayoutName = templateMetadata.getParameter("detailsLayoutName")

    assertEmpty(templateMetadata.getRelatedParams(activityTitle))
    assertSameElements(templateMetadata.getRelatedParams(layoutName), detailsLayoutName)
    assertSameElements(templateMetadata.getRelatedParams(activityClass), detailsActivity, mainFragment)
    assertSameElements(templateMetadata.getRelatedParams(mainFragment), detailsActivity, activityClass)
    assertSameElements(templateMetadata.getRelatedParams(detailsActivity), activityClass, mainFragment)
    assertSameElements(templateMetadata.getRelatedParams(detailsLayoutName), layoutName)
  }

  private fun getTestTemplatePath() : File {
    return File(getModulePath("android-templates"), FileUtil.join("testData", "TestTemplate"))
  }
}