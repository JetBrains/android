/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;

/**
 * Remote package representing the Android NDK.
 */
public class RemoteNdkPkgInfo extends RemotePkgInfo {

  public RemoteNdkPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);
    mPkgDesc = PkgDesc.Builder.newNdk(getRevision())
      .setListDisplay("Android NDK")
      .setDescriptionShort("Android NDK")
      .create();
  }

  @NonNull
  @Override
  public String installId() {
    return mPkgDesc.getInstallId();
  }

  @NonNull
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    return new File(osSdkRoot, SdkConstants.FD_NDK);
  }
}
