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
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.CheckUtil.assertDrawTreesEqual
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class LegacySnapshotLoaderTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun loadV1Snapshot() {
    val file =
      VirtualFileManager.getInstance().findFileByNioPath(TestUtils.getWorkspaceRoot().resolve("$TEST_DATA_PATH/legacy-inspector.li"))!!
    val model = InspectorModel(projectRule.project)
    val snapshotMetadata = LegacySnapshotLoader().loadFile(file, model)
    // We don't get any metadata from V1, so just verify the version
    assertThat(snapshotMetadata.snapshotVersion).isEqualTo(ProtocolVersion.Version1)

    val expected = model {
      view(7845444) {
        view(61730318) {
          view(47129653)
          view(170119739) {
            view(267431256) {
              view(140168881) {
                view(126492566) {
                  view(119317741)
                }
              }
              view(11124779) {
                view(139183649) {
                  view(193202595)
                  view(154793120)
                }
                view(257538764)
              }
            }
          }
        }
        view(99016981)
        view(119565354)
      }
    }

    assertDrawTreesEqual(expected.root, model.root)
  }

  fun loadV3Snapshot() {

  }
}