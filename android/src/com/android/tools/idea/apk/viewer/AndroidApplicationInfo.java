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
package com.android.tools.idea.apk.viewer;

import com.google.common.base.Splitter;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class AndroidApplicationInfo {
  public static final AndroidApplicationInfo UNKNOWN = new AndroidApplicationInfo("unknown", "unknown", 0);

  @NotNull public final String packageId;
  @NotNull public final String versionName;
  public final long versionCode;

  private AndroidApplicationInfo(@NotNull String packageId, @NotNull String versionName, long versionCode) {
    this.packageId = packageId;
    this.versionName = versionName;
    this.versionCode = versionCode;
  }

  public static AndroidApplicationInfo fromXmlTree(@NotNull ProcessOutput xmlTree) {
    if (xmlTree.getExitCode() != AaptInvoker.SUCCESS) {
      return UNKNOWN;
    }

    return parse(xmlTree.getStdout());
  }

  @NotNull
  static AndroidApplicationInfo parse(@NotNull String output) {
    String packageId = null;
    long versionCode = 0;
    String versionName = null;

    for (String line : Splitter.on('\n').trimResults().split(output)) {
      if (line.startsWith("A: android:versionCode")) {
        // e.g: A: android:versionCode(0x0101021b)=(type 0x10)0x2079
        int eqIndex = line.indexOf("=(type 0x10)");
        if (eqIndex > 0) {
          int endParenthesis = line.indexOf(")", eqIndex + 2);
          if (endParenthesis > 0) {
            String versionCodeStr = line.substring(endParenthesis + 1);
            versionCode = Long.decode(versionCodeStr);
          }
        }
      }
      else if (line.startsWith("A: android:versionName")) {
        // e.g: A: android:versionName(0x0101021c)="51.0.2704.10" (Raw: "51.0.2704.10")
        int eqIndex = line.indexOf("=");
        if (eqIndex > 0) {
          int endQuote = line.indexOf("\"", eqIndex + 2);
          if (endQuote > 0) {
            versionName = line.substring(eqIndex + 2, endQuote);
          }
        }
      }
      else if (line.startsWith("A: package=")) {
        // e.g: A: package="com.android.chrome" (Raw: "com.android.chrome")
        int eqIndex = line.indexOf("=");
        if (eqIndex > 0) {
          int endQuote = line.indexOf("\"", eqIndex+2);
          if (endQuote > 0) {
            packageId = line.substring(eqIndex + 2, endQuote);
          }
        }
      }

      if (packageId != null && versionName != null && versionCode != 0) {
        break;
      }
    }

    return new AndroidApplicationInfo(StringUtil.notNullize(packageId, "unknown"),
                                      StringUtil.notNullize(versionName, "?"),
                                      versionCode);
  }
}
