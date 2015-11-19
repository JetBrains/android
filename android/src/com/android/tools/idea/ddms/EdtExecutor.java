/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms;

import com.intellij.util.ui.UIUtil;

import java.util.concurrent.Executor;

public class EdtExecutor implements Executor {
  public static EdtExecutor INSTANCE = new EdtExecutor();

  private EdtExecutor() {
  }

  @Override
  public void execute(Runnable runnable) {
    // Note: Application.invokeLater() takes the ModalityState into account, but we don't need that here.
    UIUtil.invokeLaterIfNeeded(runnable);
  }
}
