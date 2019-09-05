/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import com.android.repository.Revision;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import javax.xml.bind.annotation.XmlAttribute;
import org.jetbrains.annotations.NotNull;

/**
 * What's New Assistant needs a custom bundle for the special version field,
 * which will be used to automatically open on startup if Android Studio
 * version is the same but WNA config is higher version
 */
public class WhatsNewBundle extends DefaultTutorialBundle {
  // Version is represented as major.minor.configVersion, where configVersion starts at 0
  // and should be incremented when TW team wants the panel to auto-show.
  @XmlAttribute(name = "version")
  @NotNull
  private String version = "";

  @NotNull
  public Revision getVersion() {
    return getVersion(version);
  }

  @VisibleForTesting
  @NotNull
  static Revision getVersion(@NotNull String versionString) {
    // Backwards compatibility for before periods were added
    if (!versionString.contains(".")) {
      // Pad front with zeroes to ensure at least 4 digits, otherwise parser doesn't like it
      versionString = Strings.padStart(versionString, 4, '0');

      // Assume that the first digit is major and that the last 2 digits are configVersion
      versionString = versionString.substring(0, 1) + '.'
                      + versionString.substring(1, versionString.length() - 2) + '.'
                      + versionString.substring(versionString.length() - 2);
    }

    return Revision.safeParseRevision(versionString);
  }
}
