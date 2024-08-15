/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package org.jetbrains.android.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;

/** Compat shim class for {@link AndroidPlatforms} */
public class AndroidPlatformsCompat {
  private AndroidPlatformsCompat() {}

  public static AndroidPlatformCompat getInstance(Sdk sdk) {
    return new AndroidPlatformCompat(AndroidPlatforms.getInstance(sdk));
  }

  public static AndroidPlatformCompat getInstance(Module module) {
    return new AndroidPlatformCompat(AndroidPlatforms.getInstance(module));
  }
}
