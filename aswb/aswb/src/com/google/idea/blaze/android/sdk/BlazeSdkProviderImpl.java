/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sdk;

import com.android.repository.api.ProgressIndicator;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.idea.blaze.android.sync.sdk.SdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkAdditionalDataCompat;

/** Indirection to Sdks for testing purposes. */
public class BlazeSdkProviderImpl implements BlazeSdkProvider {
  @Override
  public List<Sdk> getAllAndroidSdks() {
    return AndroidSdks.getInstance().getAllAndroidSdks();
  }

  @Override
  @Nullable
  public Sdk findSdk(String targetHash) {
    AndroidSdks androidSdks = AndroidSdks.getInstance();
    Sdk sdk = AndroidSdks.getInstance().findSuitableAndroidSdk(targetHash);
    if (SdkUtil.checkSdkAndRemoveIfInvalid(sdk)) {
      return sdk;
    }
    // We may have an android platform downloaded, but not created an IntelliJ SDK out of it.
    // If so, trigger the construction of an SDK
    ProgressIndicator progress =
        new StudioLoggerProgressIndicator(BlazeSdkProviderImpl.class);
    androidSdks.tryToChooseSdkHandler().getSdkManager(progress).reloadLocalIfNeeded(progress);

    return UIUtil.invokeAndWaitIfNeeded(
        () -> androidSdks.tryToCreate(IdeSdks.getInstance().getAndroidSdkPath(), targetHash));
  }

  @Override
  @Nullable
  public String getSdkTargetHash(Sdk sdk) {
    AndroidSdkAdditionalData additionalData = AndroidSdkAdditionalDataCompat.from(sdk);
    if (additionalData == null) {
      return null;
    }
    return additionalData.getBuildTargetHashString();
  }
}
