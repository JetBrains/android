/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.android.*;

class NewAndroidModuleSetup extends AndroidModuleSetup {
  NewAndroidModuleSetup() {
    // These are the steps to setup Android modules when they are first created.
    // These steps can be executed on module creation because, unlike dependency setup, they don't depend on the rest of the project's
    // modules.
    super(new AndroidFacetModuleSetupStep(), new SdkModuleSetupStep(), new JdkModuleSetupStep(), new ContentRootsModuleSetupStep(),
          new CompilerOutputModuleSetupStep());
  }
}
