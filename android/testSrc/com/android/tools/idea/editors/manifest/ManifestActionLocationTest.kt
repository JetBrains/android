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
package com.android.tools.idea.editors.manifest

import com.android.ide.common.blame.SourceFilePosition
import com.android.manifmerger.Actions
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class ManifestActionLocationTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()

  private val mergedManifest get() : MergedManifestSnapshot {
    return MergedManifestManager
      .getMergedManifestSupplier(projectRule.gradleModule(":app").getMainModule())
      .get()
      .get(2, TimeUnit.SECONDS)
  }

  private fun MergedManifestSnapshot.getOnlyNodeRecord(key: String): Actions.NodeRecord {
    val nodeKey = getNodeKey(key)
    assertThat(nodeKey).isNotNull()
    assertThat(actions).isNotNull()
    val records = actions!!.getNodeRecords(nodeKey!!)
    assertThat(records.size).isEqualTo(1)
    return records[0]
  }

  private fun SourceFilePosition.asText() : String? {
    return file.sourceFile?.readText()?.substring(position.startOffset, position.endOffset)?.flattenWhitespace()
  }

  private fun String.flattenWhitespace() = replace(Regex("\\s+"), " ")

  private fun findProjectRelativeFile(vararg segments: String): File {
    val vFile =  VfsUtil.findRelativeFile(projectRule.project.guessProjectDir(), *segments)
    assertThat(vFile).isNotNull()
    return vFile!!.toIoFile()
  }

  @Before
  fun setup() = projectRule.load(NAVIGATION_EDITOR_INCLUDE_FROM_LIB)

  @Test
  fun getActionLocation_navigationFile() {
    val browsableRecord = mergedManifest.getOnlyNodeRecord("category#android.intent.category.BROWSABLE")
    val browsablePosition = getActionLocation(browsableRecord)

    assertThat(browsablePosition.file.sourceFile).isNotNull()
    assertThat(browsablePosition.file.sourceFile)
      .isEqualTo(findProjectRelativeFile("lib", "src", "main", "res", "navigation", "lib_nav.xml"))

    assertThat(browsablePosition.asText())
      .isEqualTo("<deepLink android:id=\"@+id/deepLink\" app:uri=\"https://www.google.com\" />")
  }

  @Test
  fun getActionLocation_libraryManifest() {
    val metaDataRecord = mergedManifest.getOnlyNodeRecord("meta-data#libMetaData")
    val metaDataPosition = getActionLocation(metaDataRecord)

    assertThat(metaDataPosition.file.sourceFile).isNotNull()
    assertThat(metaDataPosition.file.sourceFile)
      .isEqualTo(findProjectRelativeFile("lib", "src", "main", "AndroidManifest.xml"))

    // TODO(b/146513553): Currently we're only able to find the correct file for nodes from lib manifests,
    //  but we should be able to find the correct line / column number as well.
    //assertThat(metaDataPosition.asText())
    //  .isEqualTo("<meta-data android:name=\"libMetaData\" android:value=\"\"/>")
  }

  @Test
  fun getActionLocation_primaryManifest() {
    val launcherRecord = mergedManifest.getOnlyNodeRecord("action#android.intent.action.MAIN")
    val launcherPosition = getActionLocation(launcherRecord)

    assertThat(launcherPosition.file.sourceFile).isNotNull()
    assertThat(launcherPosition.file.sourceFile)
      .isEqualTo(findProjectRelativeFile("app", "src", "main", "AndroidManifest.xml"))
    assertThat(launcherPosition.asText())
      .isEqualTo("<action android:name=\"android.intent.action.MAIN\"/>")
  }

  private fun getActionLocation(browsableRecord: Actions.NodeRecord): SourceFilePosition {
    return runReadAction {  ManifestUtils.getActionLocation(projectRule.gradleModule(":app").getMainModule(), browsableRecord) }
  }
}