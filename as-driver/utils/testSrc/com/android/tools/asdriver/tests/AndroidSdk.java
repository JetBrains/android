/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.testutils.TestUtils;
import java.nio.file.Path;
import java.util.HashMap;

public class AndroidSdk {

  // TODO: This is a place holder SDK class, that will need more when we start using its contents
  public void install(HashMap<String, String> env) {
    Path path = TestUtils.resolveWorkspacePath("prebuilts/studio/sdk/linux");
    env.put("ANDROID_HOME", path.toString());
  }
}
