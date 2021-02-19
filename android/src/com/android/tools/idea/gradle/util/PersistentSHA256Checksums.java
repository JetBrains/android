/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "PersistentGradleDistributionSHA256",
  storages = @Storage("cachedGradleSHA256.xml")
)
public class PersistentSHA256Checksums implements PersistentStateComponent<PersistentSHA256Checksums> {
  public HashMap<String, String> myStoredChecksums;

  public static PersistentSHA256Checksums getInstance() {
    return ApplicationManager.getApplication().getService(PersistentSHA256Checksums.class);
  }

  @Override
  public PersistentSHA256Checksums getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PersistentSHA256Checksums state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  /**
   * Verifies if the passed checksum is already stored for the given distribution.
   * @param distribution
   * @param checksum
   * @return {@code true} if the stored checksum for the given {@param distribution} matches with {@param checksum}
   */
  public boolean isChecksumStored(@Nullable String distribution, @Nullable String checksum) {
    if (myStoredChecksums == null) {
      return false;
    }
    if (StringUtil.isEmptyOrSpaces(distribution) || StringUtil.isEmptyOrSpaces(checksum)) {
      return false;
    }
    return checksum.equals(myStoredChecksums.getOrDefault(distribution, null));
  }

  /**
   * Stores the checksum for the given distribution
   * @param distribution
   * @param checksum
   */
  public void storeChecksum(@NotNull String distribution, @NotNull String checksum) {
    if (myStoredChecksums == null) {
      myStoredChecksums = new HashMap<>();
    }
    myStoredChecksums.put(distribution, checksum);
  }
}

