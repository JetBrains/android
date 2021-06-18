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
package com.android.tools.idea.gradle.dsl.model.android.testOptions;

import com.android.tools.idea.gradle.dsl.api.android.testOptions.EmulatorSnapshotsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.EmulatorSnapshotsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EmulatorSnapshotsModelImpl extends GradleDslBlockModel implements EmulatorSnapshotsModel {
  public static final @NonNls String COMPRESS_SNAPSHOTS = "mCompressSnapshots";
  public static final @NonNls String ENABLE_FOR_TEST_FAILURES = "mEnableForTestFailures";
  public static final @NonNls String MAX_SNAPSHOTS_FOR_TEST_FAILURES = "mMaxSnapshotsForTestFailures";

  public EmulatorSnapshotsModelImpl(@NotNull EmulatorSnapshotsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  public @NotNull ResolvedPropertyModel compressSnapshots() {
    return getModelForProperty(COMPRESS_SNAPSHOTS);
  }

  @Override
  public @NotNull ResolvedPropertyModel enableForTestFailures() {
    return getModelForProperty(ENABLE_FOR_TEST_FAILURES);
  }

  @Override
  public @NotNull ResolvedPropertyModel maxSnapshotsForTestFailures() {
    return getModelForProperty(MAX_SNAPSHOTS_FOR_TEST_FAILURES);
  }
}
