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
package com.android.tools.idea.lang.proguard;

import com.android.SdkConstants;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileNameMatcherEx;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class ProguardFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(ProguardFileType.INSTANCE, new ProguardNameMatcher());
    if (StudioFlags.R8_SUPPORT_ENABLED.get() || ApplicationManager.getApplication().isUnitTestMode()) {
      consumer.consume(ProguardR8FileType.INSTANCE, new ProguardR8NameMatcher());
    }
  }

  private static class ProguardNameMatcher extends FileNameMatcherEx {
    @Override
    public boolean acceptsCharSequence(@NotNull CharSequence fileName) {
      return !StudioFlags.R8_SUPPORT_ENABLED.get() && (StringUtil.endsWith(fileName, ProguardFileType.DOT_PRO) ||
                                                       StringUtil.startsWith(fileName, "proguard-") &&
                                                       StringUtil.endsWith(fileName, SdkConstants.DOT_TXT) ||
                                                       StringUtil.equals(fileName, SdkConstants.OLD_PROGUARD_FILE))
        ;
    }

    @NotNull
    @Override
    public String getPresentableString() {
      return "*.pro or proguard-*.txt or proguard.cfg";
    }
  }

  public static class ProguardR8NameMatcher extends FileNameMatcherEx {
    @Override
    public boolean acceptsCharSequence(@NotNull CharSequence fileName) {
      return StudioFlags.R8_SUPPORT_ENABLED.get() && (StringUtil.endsWith(fileName, ProguardR8FileType.DOT_PRO) ||
             StringUtil.startsWith(fileName, "proguard-") && StringUtil.endsWith(fileName, SdkConstants.DOT_TXT) ||
                                                      StringUtil.equals(fileName, SdkConstants.OLD_PROGUARD_FILE))
        ;
    }

    @NotNull
    @Override
    public String getPresentableString() {
      return "*.pro or proguard-*.txt or proguard.cfg";
    }
  }
}
