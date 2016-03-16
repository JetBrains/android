/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.sdk.legacy.remote.internal.archives;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The checksum type.
 */
public enum ChecksumType {
  /**
   * A SHA1 checksum, represented as a 40-hex string.
   */
  SHA1("SHA-1");  //$NON-NLS-1$

  private final String mAlgorithmName;

  /**
   * Constructs a {@link com.android.tools.idea.sdk.legacy.remote.internal.archives.ChecksumType} with the algorithm name
   * suitable for {@link MessageDigest#getInstance(String)}.
   * <p/>
   * These names are officially documented at
   * http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#MessageDigest
   */
  ChecksumType(String algorithmName) {
    mAlgorithmName = algorithmName;
  }

  /**
   * Returns a new {@link MessageDigest} instance for this checksum type.
   *
   * @throws NoSuchAlgorithmException if this algorithm is not available.
   */
  public MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(mAlgorithmName);
  }
}
