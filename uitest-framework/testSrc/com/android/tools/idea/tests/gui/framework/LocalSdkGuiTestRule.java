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
package com.android.tools.idea.tests.gui.framework;

import com.android.SdkConstants;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.io.File;

/**
 * Small extension of GuiTestRule that uses the SDK referred to by the environment variable ANDROID_HOME.
 * This is intended only for use in TestGroup.QA currently, and even then only for tests that cannot use the prebuilt SDK
 * for some reason.
 */
public class LocalSdkGuiTestRule extends GuiTestRule {
  @Override
  protected void setUpSdks() {
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction((Runnable) () ->
      IdeSdks.getInstance().setAndroidSdkPath(new File(System.getenv(SdkConstants.ANDROID_HOME_ENV)), null)
    ), ModalityState.defaultModalityState());
  }
}
