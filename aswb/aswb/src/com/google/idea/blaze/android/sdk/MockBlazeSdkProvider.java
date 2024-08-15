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

import com.google.common.collect.Maps;
import com.intellij.openapi.projectRoots.Sdk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** Indirection to Sdks for testing purposes. */
public class MockBlazeSdkProvider implements BlazeSdkProvider {
  Map<String, Sdk> sdks = Maps.newHashMap();

  public void addSdk(String targetHash, Sdk sdk) {
    sdks.put(targetHash, sdk);
  }

  @Override
  public List<Sdk> getAllAndroidSdks() {
    return new ArrayList<>(sdks.values());
  }

  @Override
  public Sdk findSdk(String targetHash) {
    return sdks.get(targetHash);
  }

  @Override
  @Nullable
  public String getSdkTargetHash(Sdk sdk) {
    return sdks.entrySet()
        .stream()
        .filter(entry -> entry.getValue() == sdk)
        .map(Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
