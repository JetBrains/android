// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidOSProcessHandler extends BaseOSProcessHandler {
  @NonNls private static final String IGNORING = "ignoring";
  @NonNls private static final String SKIPPING = "skipping";
  @NonNls private static final String DEBUGGABLE_ERROR = "androidmanifest.xml already defines debuggable";

  private final List<String> myInfoMessages = new ArrayList<>();
  private final List<String> myErrorMessages = new ArrayList<>();

  public AndroidOSProcessHandler(@NotNull final Process process, @NotNull final String commandLine) {
    super(process, commandLine, null);
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    super.notifyTextAvailable(text, outputType);

    if (StringUtil.isEmptyOrSpaces(text)) {
      return;
    }
    String[] lines = text.split("[\\n\\r]+");
    for (String line : lines) {
      String l = line.toLowerCase();
      if (outputType == ProcessOutputTypes.STDOUT) {
        myInfoMessages.add(line);
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        if (l.contains(IGNORING) || l.contains(SKIPPING) || l.contains(DEBUGGABLE_ERROR)) {
          myInfoMessages.add(line);
        }
        else {
          myErrorMessages.add(line);
        }
      }
    }
  }

  @NotNull
  public List<String> getInfoMessages() {
    return myInfoMessages;
  }

  @NotNull
  public List<String> getErrorMessages() {
    return myErrorMessages;
  }
}
