import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import java.io.File

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

const val TEST_DATA_PATH = "tools/adt/idea/androidx-integration-tests/testData"
const val SIMPLE_COMPOSE_PROJECT_PATH = "projects/SimpleComposeApplication"

const val ANDROIDX_SNAPSHOT_REPO_PATH = "prebuilts/tools/common/androidx-integration/m2repository"

enum class ComposeTestProject(
  override val template: String,
): TemplateBasedTestProject {
  SIMPLE_COMPOSE_PROJECT(SIMPLE_COMPOSE_PROJECT_PATH)
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TEST_DATA_PATH

  override fun getAdditionalRepos(): Collection<File> =
    listOf(resolveWorkspacePath(ANDROIDX_SNAPSHOT_REPO_PATH).toFile())

}