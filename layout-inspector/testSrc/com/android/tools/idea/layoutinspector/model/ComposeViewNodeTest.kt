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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val MATERIAL = packageNameHash("androidx.compose.material")
private val FOUNDATION_TEXT = packageNameHash("androidx.compose.foundation.text")
private val EXAMPLE = packageNameHash("com.example.myexampleapp")

class ComposeViewNodeTest {

  @Test
  fun testIsSystemNode() {
    val model = model {
      view(ROOT) {
        compose(VIEW1, "MyApplicationTheme", composePackageHash = EXAMPLE) {
          compose(VIEW2, "Text", composePackageHash = EXAMPLE) {
            compose(VIEW3, "Text", composePackageHash = MATERIAL) {
              compose(VIEW4, "CoreText", composePackageHash = FOUNDATION_TEXT)
            }
          }
        }
      }
    }

    val user1 = model[VIEW1]!!
    val user2 = model[VIEW2]!!
    val system1 = model[VIEW3]!!
    val system2 = model[VIEW4]!!
    assertThat(system1.isSystemNode).isTrue()
    assertThat(system2.isSystemNode).isTrue()
    assertThat(user1.isSystemNode).isFalse()
    assertThat(user2.isSystemNode).isFalse()

    val treeSettings = FakeTreeSettings()
    treeSettings.hideSystemNodes = true
    assertThat(system1.isInComponentTree(treeSettings)).isFalse()
    assertThat(system2.isInComponentTree(treeSettings)).isFalse()
    assertThat(user1.isInComponentTree(treeSettings)).isTrue()
    assertThat(user2.isInComponentTree(treeSettings)).isTrue()

    treeSettings.hideSystemNodes = false
    assertThat(system1.isInComponentTree(treeSettings)).isTrue()
    assertThat(system2.isInComponentTree(treeSettings)).isTrue()
    assertThat(user1.isInComponentTree(treeSettings)).isTrue()
    assertThat(user2.isInComponentTree(treeSettings)).isTrue()
  }

  @Test
  fun testFlags() {
    val model = model {
      view(ROOT) {
        compose(VIEW1, "MyApplicationTheme") {
          compose(VIEW2, "Column", composeFlags = FLAG_IS_INLINED, composePackageHash = EXAMPLE) {
            compose(VIEW3, "Text", composeFlags = FLAG_HAS_MERGED_SEMANTICS, composePackageHash = EXAMPLE) {
              compose(VIEW4, "Text", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS, composePackageHash = EXAMPLE) {
                compose(VIEW5, "CoreText", composeFlags = FLAG_SYSTEM_DEFINED, composePackageHash = EXAMPLE)
              }
            }
          }
        }
      }
    }
    assertThat(model[VIEW2]?.isInlined).isTrue()
    assertThat(model[VIEW2]?.isSystemNode).isFalse()
    assertThat(model[VIEW2]?.hasMergedSemantics).isFalse()
    assertThat(model[VIEW2]?.hasUnmergedSemantics).isFalse()

    assertThat(model[VIEW3]?.isInlined).isFalse()
    assertThat(model[VIEW3]?.isSystemNode).isFalse()
    assertThat(model[VIEW3]?.hasMergedSemantics).isTrue()
    assertThat(model[VIEW3]?.hasUnmergedSemantics).isFalse()

    assertThat(model[VIEW4]?.isInlined).isFalse()
    assertThat(model[VIEW4]?.hasMergedSemantics).isFalse()
    assertThat(model[VIEW4]?.hasUnmergedSemantics).isTrue()
    assertThat(model[VIEW4]?.isSystemNode).isFalse()

    assertThat(model[VIEW5]?.isInlined).isFalse()
    assertThat(model[VIEW5]?.hasMergedSemantics).isFalse()
    assertThat(model[VIEW5]?.hasUnmergedSemantics).isFalse()
    assertThat(model[VIEW5]?.isSystemNode).isTrue()
  }
}
