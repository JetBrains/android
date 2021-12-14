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
package com.android.tools.idea

import com.android.tools.idea.gradle.project.sync.snapshots.IdeV2ModelSnapshotComparisonTest
import com.android.tools.idea.gradle.project.sync.snapshots.IdeModelSnapshotComparisonTest
import com.android.tools.idea.gradle.project.sync.snapshots.IdeModelSnapshotComparisonOldAgpTest
import org.junit.runner.RunWith
import org.junit.runners.Suite


/**
 * This test suite is not supposed to run in Bazel, and it is supposed to be used as a convenient shortcut to run all tests required to
 * update IDE model snapshot files.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
  IdeV2ModelSnapshotComparisonTest::class,
  IdeModelSnapshotComparisonTest::class,
  IdeModelSnapshotComparisonOldAgpTest::class
)
class IdeModelTestSuite