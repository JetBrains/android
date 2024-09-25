/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.idea.blaze.android;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sdk.BlazeSdkProviderImpl;
import com.google.idea.blaze.android.sdk.MockBlazeSdkProvider;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;

/**
 * Helper to create mock SDKs for light test case.
 *
 * <p>Consider light test case cannot create files in disk, they cannot create register SDK by using
 * {link MockSdkUtil#registerSdk}. This helper class provide alternative method to register mock
 * SDK.
 */
public class LightWeightMockSdkUtil {

  private LightWeightMockSdkUtil() {}

  /**
   * A light weight way to mock SDK.
   *
   * <p>This method help light sync test cases to retrieve sdk of project without creating dummy sdk
   * files. SDK will be maintained by {@link MockBlazeSdkProvider} instead of {@link
   * ProjectJdkTable}. But all functions in {@link BlazeSdkProviderImpl} cannot be test due to
   * override by {@link MockBlazeSdkProvider}
   *
   * <p>Note that user is expected to register MockBlazeSdkProvider as application service before
   * this method.
   *
   * @param targetHash code name of SDK
   * @param sdkName display name of SDK
   * @param sdkProvider the MockBlazeSdkProvider to register mock sdk
   * @return a mock sdk for the given target and name. The sdk is registered with the {@link
   *     MockBlazeSdkProvider}. Note that this SDK is not added to {@link ProjectJdkTable}
   */
  public static Sdk registerSdk(
      String targetHash, String sdkName, MockBlazeSdkProvider sdkProvider) {
    SdkTypeId sdkType = mock(SdkTypeId.class);
    when(sdkType.getName()).thenReturn("Android SDK");
    Sdk sdk = mock(Sdk.class);
    when(sdk.getName()).thenReturn(sdkName);
    when(sdk.getSdkType()).thenReturn(sdkType);
    assert sdkProvider.equals(BlazeSdkProvider.getInstance());
    sdkProvider.addSdk(targetHash, sdk);
    return sdk;
  }
}
