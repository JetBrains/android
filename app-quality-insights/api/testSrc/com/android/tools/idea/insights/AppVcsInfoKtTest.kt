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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.proto.BuildStamp
import com.android.tools.idea.insights.proto.RepositoryInfo
import com.android.tools.idea.insights.proto.VersionControlSystem
import com.android.tools.idea.protobuf.TextFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVcsInfoKtTest {
  @Test
  fun `decode empty text`() {
    val default = BuildStamp.getDefaultInstance()
    val textProto = TextFormat.printer().printToString(default)

    assertThat(AppVcsInfo.fromProto(textProto)).isEqualTo(AppVcsInfo.NONE)
  }

  // TODO: add more for error cases

  @Test
  fun `decode sample text proto`() {
    val original = SAMPLE_APP_VCS_INFO_BUILDER.build()
    val textProto = TextFormat.printer().printToString(original)

    assertThat(AppVcsInfo.fromProto(textProto))
      .isEqualTo(
        AppVcsInfo(
          listOf(
            RepoInfo(
              vcsKey = VCS_CATEGORY.GIT,
              rootPath = PROJECT_ROOT_PREFIX,
              revision = REVISION_74081e5f
            )
          )
        )
      )
  }
}

private const val REVISION_74081e5f = "74081e5f56a58788f3243fe8410c4b66e9c7c902"

private val SAMPLE_REPO_BUILDER =
  RepositoryInfo.newBuilder().apply {
    system = VersionControlSystem.GIT
    localRootPath = PROJECT_ROOT_PREFIX
    revision = REVISION_74081e5f
  }

private val SAMPLE_APP_VCS_INFO_BUILDER =
  BuildStamp.newBuilder().apply { addRepositories(SAMPLE_REPO_BUILDER.build()) }
