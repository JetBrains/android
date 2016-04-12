/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Application id provider for non-Gradle projects.
 */
public class NonGradleApplicationIdProvider implements ApplicationIdProvider {
  @NotNull
  private final AndroidFacet myFacet;

  public NonGradleApplicationIdProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  @NotNull
  public String getPackageName() throws ApkProvisionException {
    return ApkProviderUtil.computePackageName(myFacet);
  }

  @Override
  public String getTestPackageName() throws ApkProvisionException {
    return ApkProviderUtil.computePackageName(myFacet);
  }
}
