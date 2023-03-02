/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidProcessText {
  private static final Key<AndroidProcessText> KEY = new Key<AndroidProcessText>("ANDROID_PROCESS_TEXT");

  private final List<MyFragment> myFragments = new ArrayList<MyFragment>();

  private AndroidProcessText() {
  }

  private void setupListeners(@NotNull ProcessHandler processHandler) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        synchronized (myFragments) {
          myFragments.add(new MyFragment(event.getText(), outputType));
        }
      }
    });
    processHandler.putUserData(KEY, this);
  }

  public static void attach(@NotNull ProcessHandler processHandler) {
    AndroidProcessText text = new AndroidProcessText();
    text.setupListeners(processHandler);
  }

  @Nullable
  public static AndroidProcessText get(@NotNull ProcessHandler processHandler) {
    return processHandler.getUserData(KEY);
  }

  public void printTo(@NotNull ProcessHandler processHandler) {
    synchronized (myFragments) {
      for (MyFragment fragment : myFragments) {
        processHandler.notifyTextAvailable(fragment.getText(), fragment.getOutputType());
      }
    }
  }

  private static class MyFragment {
    private final String myText;
    private final Key myOutputType;

    private MyFragment(@NotNull String text, @NotNull Key outputType) {
      myText = text;
      myOutputType = outputType;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public Key getOutputType() {
      return myOutputType;
    }
  }
}
