// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AndroidProcessText {
  private static final Key<AndroidProcessText> KEY = new Key<>("ANDROID_PROCESS_TEXT");

  private final List<MyFragment> myFragments = new ArrayList<>();

  private AndroidProcessText(@NotNull ProcessHandler processHandler) {
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
    new AndroidProcessText(processHandler);
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
