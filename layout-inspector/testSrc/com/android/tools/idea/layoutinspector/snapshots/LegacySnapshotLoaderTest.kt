/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.CheckUtil.ANY_DRAW_ID
import com.android.tools.idea.layoutinspector.util.CheckUtil.assertDrawTreesEqual
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.SNAPSHOT_CLIENT
import com.intellij.testFramework.ProjectRule
import layoutinspector.snapshots.Metadata
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import javax.imageio.ImageIO

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class LegacySnapshotLoaderTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val testDataPath = TestUtils.resolveWorkspacePathUnchecked(TEST_DATA_PATH)

  @Test
  fun loadV1Snapshot() {
    val model = InspectorModel(projectRule.project)
    val stats = SessionStatisticsImpl(SNAPSHOT_CLIENT, model)
    val snapshotMetadata = LegacySnapshotLoader().loadFile(testDataPath.resolve("legacy-snapshot-v1.li"), model, stats)
    // We don't get any metadata from V1, so just verify the version
    assertThat(snapshotMetadata.snapshotVersion).isEqualTo(ProtocolVersion.Version1)

    validateModel(model)
  }

  @Test
  fun loadV3Snapshot() {
    val model = InspectorModel(projectRule.project)
    val stats = SessionStatisticsImpl(SNAPSHOT_CLIENT, model)
    val snapshotMetadata = LegacySnapshotLoader().loadFile(testDataPath.resolve("legacy-snapshot-v3.li"), model, stats)
    assertThat(snapshotMetadata.snapshotVersion).isEqualTo(ProtocolVersion.Version3)
    assertThat(snapshotMetadata.apiLevel).isEqualTo(27)
    assertThat(snapshotMetadata.processName).isEqualTo("com.example.myapplication")
    assertThat(snapshotMetadata.containsCompose).isFalse()
    assertThat(snapshotMetadata.liveDuringCapture).isFalse()
    assertThat(snapshotMetadata.source).isEqualTo(Metadata.Source.STUDIO)
    assertThat(snapshotMetadata.sourceVersion).isEqualTo("dev build")

    validateModel(model)
  }

  private fun validateModel(model: InspectorModel) {
    val window = model.windows.values.single()
    window.refreshImages(1.0)
    val expected = model {
      view(ANY_DRAW_ID) {
        Files.newInputStream(testDataPath.resolve("legacy-snapshot.png")).use { imageInput ->
          image(ImageIO.read(imageInput))
        }
        view(ANY_DRAW_ID) {
          view(ANY_DRAW_ID)
          view(ANY_DRAW_ID) {
            view(ANY_DRAW_ID) {
              view(ANY_DRAW_ID) {
                view(ANY_DRAW_ID) {
                  view(ANY_DRAW_ID)
                }
              }
              view(ANY_DRAW_ID) {
                view(ANY_DRAW_ID) {
                  view(ANY_DRAW_ID)
                  view(ANY_DRAW_ID)
                }
                view(ANY_DRAW_ID)
              }
            }
          }
        }
        view(ANY_DRAW_ID)
        view(ANY_DRAW_ID)
      }
    }

    assertDrawTreesEqual(expected.root, model.root)
  }
}