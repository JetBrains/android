/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Objects;

/**
 * Reports progress of the SDK verification.
 */
public class VerifySdkStep extends ProgressStep {
  private final ScopedStateStore.Key<Boolean> myKeyShouldDownload;

  public VerifySdkStep(ScopedStateStore.Key<Boolean> keyShouldDownload) {
    super("Android SDK Verification", "Verifying necessary Android SDK components");
    myKeyShouldDownload = keyShouldDownload;
  }

  @Override
  public boolean isStepVisible() {
    return !Objects.equal(Boolean.TRUE, myState.get(myKeyShouldDownload));
  }
}
