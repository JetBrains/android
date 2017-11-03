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
package com.android.tools.idea.testing;

import com.android.annotations.Nullable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import org.intellij.lang.annotations.MagicConstant;

/**
 * Allows to mock message dialogs in headless integration tests.
 * <p>
 * Sample usage:
 * <pre>
 *   // Replace the default dialog used by com.intellij.openapi.ui.Messages with a mock one.
 *   TestMessagesDialog testDialog = new TestMessagesDialog(Messages.OK);
 *   Messages.setTestDialog(testDialog);
 * </pre>
 * </p>
 */
public class TestMessagesDialog implements TestDialog {
  private final int myAnswer;
  private String myDisplayedMessage;

  public TestMessagesDialog(@MagicConstant(valuesFromClass = Messages.class) int answer) {
    myAnswer = answer;
  }

  @Override
  public int show(String message) {
    myDisplayedMessage = message;
    return myAnswer;
  }

  @Nullable
  public String getDisplayedMessage() {
    return myDisplayedMessage;
  }
}
