/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import org.jetbrains.annotations.NonNls;

public final class GradleBuilds {
  @NonNls public static final String ASSEMBLE_TRANSLATE_TASK_NAME = "assembleTranslate";
  @NonNls public static final String CLEAN_TASK_NAME = "clean";
  @NonNls public static final String DEFAULT_ASSEMBLE_TASK_NAME = "assemble";

  @NonNls public static final String ENABLE_TRANSLATION_JVM_ARG = "enableTranslation";

  @NonNls public static final String OFFLINE_MODE_OPTION = "--offline";
  @NonNls public static final String PARALLEL_BUILD_OPTION = "--parallel";
  @NonNls public static final String CONFIGURE_ON_DEMAND_OPTION = "--configure-on-demand";

  @NonNls public static final String BUILD_SRC_FOLDER_NAME = "buildSrc";

  private GradleBuilds() {
  }
}
