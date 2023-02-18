/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api.launch

import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate.Type.AAR
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtifactCoordinateTest {
  @Test
  fun testSameArtifact() {
    assertThat(
        ArtifactCoordinate("g1", "a1", "1.0", AAR)
          .sameArtifact(ArtifactCoordinate("g1", "a1", "1.0", AAR))
      )
      .isTrue()
    assertThat(
        ArtifactCoordinate("g1", "a1", "1.0", AAR)
          .sameArtifact(ArtifactCoordinate("g1", "a1", "2.0", AAR))
      )
      .isTrue()
    assertThat(
        ArtifactCoordinate("g1", "a1", "1.0", AAR)
          .sameArtifact(ArtifactCoordinate("g1", "a2", "1.0", AAR))
      )
      .isFalse()
    assertThat(
        ArtifactCoordinate("g1", "a1", "1.0", AAR)
          .sameArtifact(ArtifactCoordinate("g2", "a1", "1.0", AAR))
      )
      .isFalse()
    assertThat(
        ArtifactCoordinate("g1", "a1", "1.0", AAR)
          .sameArtifact(ArtifactCoordinate("g2", "a2", "1.0", AAR))
      )
      .isFalse()
  }
}
