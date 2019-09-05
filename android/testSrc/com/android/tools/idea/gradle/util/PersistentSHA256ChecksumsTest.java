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

import static com.google.common.truth.Truth.assertThat;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link PersistentSHA256Checksums}.
 */
public class PersistentSHA256ChecksumsTest {
  private static String DISTRIBUTION = "https://services.gradle.org/distributions/gradle-5.6.2-bin.zip";
  private static String SHA256 = "0986244820e4a35d32d91df2ec4b768b5ba5d6c8246753794f85159f9963ec12";

  @Test
  public void testChecksumStored() {
    String DISTRIBUTION_OTHER = "https://services.gradle.org/distributions/gradle-5.6.3-bin.zip";
    String SHA256_OTHER = "NOT_VALID_SHA256";

    PersistentSHA256Checksums checksums = new PersistentSHA256Checksums();
    // Should be empty initially
    assertThat(checksums.myStoredChecksums == null || checksums.myStoredChecksums.isEmpty()).isTrue();
    verifyStored(checksums, false);
    // Add a different entry to the one that is looked for
    checksums.storeChecksum(DISTRIBUTION_OTHER, SHA256);
    assertThat(checksums.myStoredChecksums).hasSize(1);
    verifyStored(checksums, false);
    // Add the same entry but with different SHA256
    checksums.storeChecksum(DISTRIBUTION, SHA256_OTHER);
    assertThat(checksums.myStoredChecksums).hasSize(2);
    verifyStored(checksums, false);
    // Replace existing entry with the expected SHA256
    checksums.storeChecksum(DISTRIBUTION, SHA256);
    assertThat(checksums.myStoredChecksums).hasSize(2);
    verifyStored(checksums, true);
  }

  private static void verifyStored(@NotNull PersistentSHA256Checksums checksums, boolean shouldBeStored) {
    assertThat(checksums.isChecksumStored(null, null)).isFalse();
    assertThat(checksums.isChecksumStored(null, " ")).isFalse();
    assertThat(checksums.isChecksumStored(null, SHA256)).isFalse();
    assertThat(checksums.isChecksumStored(" ", null)).isFalse();
    assertThat(checksums.isChecksumStored(" ", " ")).isFalse();
    assertThat(checksums.isChecksumStored(" ", SHA256)).isFalse();
    assertThat(checksums.isChecksumStored(DISTRIBUTION, null)).isFalse();
    assertThat(checksums.isChecksumStored(DISTRIBUTION, " ")).isFalse();
    assertThat(checksums.isChecksumStored(DISTRIBUTION, SHA256)).isEqualTo(shouldBeStored);
  }
}
