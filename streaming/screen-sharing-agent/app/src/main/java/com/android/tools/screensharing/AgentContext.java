/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.screensharing;

import android.content.Context;
import android.content.ContextWrapper;

public final class AgentContext extends ContextWrapper {
  public static final AgentContext INSTANCE = new AgentContext();  // Accessed from native code.
  private static final String PACKAGE_NAME = "com.android.shell";

  private AgentContext() {
    super(getSystemContext());
  }

  @Override
  public String getPackageName() {
    return PACKAGE_NAME;
  }

  @Override
  public String getOpPackageName() {
    return PACKAGE_NAME;
  }

  @Override
  public Context getApplicationContext() {
    return this;
  }

  @Override
  public Context createPackageContext(String packageName, int flags) {
    return this;
  }

  private static native Context getSystemContext();
}
