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
package org.jetbrains.android;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Various utility methods for unit tests
 */
public class AndroidTestUtils {
  /**
   * Get an {@link IntentionAction} with given name
   */
  @Nullable/*if no intention with given message hasn't found*/
  public static IntentionAction getIntentionAction(@NotNull JavaCodeInsightTestFixture fixture,
                                                   @NotNull String message) {
    for (IntentionAction action : fixture.getAvailableIntentions()) {
      String text = action.getText();
      if (message.equals(text)) {
        return action;
      }
    }

    return null;
  }

  /**
   * Get an intention action with given name and class
   */
  @Nullable/*if no such intention has found*/
  public static <T extends IntentionAction> T getIntentionAction(@NotNull JavaCodeInsightTestFixture fixture,
                                                                 @NotNull Class<T> aClass,
                                                                 @NotNull String message) {
    for (IntentionAction action : fixture.getAvailableIntentions()) {
      if (!message.equals(action.getText())) {
        continue;
      }
      if (action instanceof IntentionActionDelegate) {
        action = ((IntentionActionDelegate)action).getDelegate();
      }
      if (aClass.isAssignableFrom(action.getClass())) {
        //noinspection unchecked
        return (T)action;
      }
    }
    return null;
  }
}
