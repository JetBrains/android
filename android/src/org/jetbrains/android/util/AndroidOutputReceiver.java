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

package org.jetbrains.android.util;

import com.android.ddmlib.MultiLineReceiver;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidOutputReceiver extends MultiLineReceiver {
  private static final String BAD_ACCESS_ERROR = "Bad address (14)";
  private boolean myTryAgain;

  public AndroidOutputReceiver() {
    // We handling all trimming ourselves, in AndroidLogcatReceiver and supporting classes
    setTrimLine(false);
  }

  @Override
  public final void processNewLines(String[] lines) {
    if (myTryAgain) {
      return;
    }
    List<String> newLines = new ArrayList<>();
    for (String line : lines) {
      newLines.add(line);
      if (line.contains(BAD_ACCESS_ERROR)) {
        myTryAgain = true;
        break;
      }
    }
    processNewLines(newLines);
  }

  public boolean isTryAgain() {
    return myTryAgain;
  }

  public void invalidate() {
    myTryAgain = false;
  }

  protected abstract void processNewLines(@NotNull List<String> newLines);
}
