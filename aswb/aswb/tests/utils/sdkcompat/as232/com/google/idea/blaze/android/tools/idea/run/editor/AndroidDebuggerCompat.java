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
package com.google.idea.blaze.android.tools.idea.run.editor;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerInfoProvider;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import java.util.List;

/**
 * Provides SDK compatibility for AndroidDebugger classes, available to ASwB.
 *
 * <p>The package name of AndroidDebugger and AndroidJavaDebugger get changed since Android Studio
 * 2022.2. So test class cannot import same package name for different sdks when using them. This
 * class allows each sdk to import its own.
 */
public final class AndroidDebuggerCompat {
  @SuppressWarnings({"rawtypes"}) // / Raw type from upstream.
  public static List<AndroidDebugger> getAndroidDebuggers(
      AndroidDebuggerInfoProvider debuggerInfoProvider,
      BlazeCommandRunConfiguration blazeCommandRunConfiguration) {
    return debuggerInfoProvider.getAndroidDebuggers(blazeCommandRunConfiguration);
  }

  @SuppressWarnings({"rawtypes"}) // / Raw type from upstream.
  public static AndroidDebugger getSelectedAndroidDebugger(
      AndroidDebuggerInfoProvider debuggerInfoProvider,
      BlazeCommandRunConfiguration blazeCommandRunConfiguration) {
    return debuggerInfoProvider.getSelectedAndroidDebugger(blazeCommandRunConfiguration);
  }

  public static AndroidJavaDebugger getAndroidJavaDebugger() {
    return new AndroidJavaDebugger();
  }

  @SuppressWarnings({"rawtypes"}) // Raw type from upstream.
  public static List<AndroidDebugger> getAvailableDebuggerExtensionList() {
    return AndroidDebugger.EP_NAME.getExtensionList();
  }

  private AndroidDebuggerCompat() {}
}
