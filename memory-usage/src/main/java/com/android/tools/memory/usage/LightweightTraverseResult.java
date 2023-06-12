/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.memory.usage;

public class LightweightTraverseResult {
  private final int totalObjectsNumber;
  private final long totalObjectsSizeBytes;
  private final int totalReachableObjectsNumber;
  private final long totalReachableObjectsSizeBytes;
  private final int totalStrongReferencedObjectsNumber;
  private final long totalStrongReferencedObjectsSizeBytes;

  public LightweightTraverseResult(int totalObjectsNumber,
                                   long totalObjectsSize,
                                   int totalReachableObjectsNumber,
                                   long totalReachableObjectsSize,
                                   int totalStrongReferencedObjectsNumber,
                                   long totalStrongReferencedObjectsSize) {
    this.totalObjectsNumber = totalObjectsNumber;
    this.totalObjectsSizeBytes = totalObjectsSize;

    this.totalReachableObjectsNumber = totalReachableObjectsNumber;
    this.totalReachableObjectsSizeBytes = totalReachableObjectsSize;

    this.totalStrongReferencedObjectsNumber = totalStrongReferencedObjectsNumber;
    this.totalStrongReferencedObjectsSizeBytes = totalStrongReferencedObjectsSize;
  }

  public int getTotalObjectsNumber() {
    return totalObjectsNumber;
  }

  public long getTotalObjectsSizeBytes() {
    return totalObjectsSizeBytes;
  }

  public long getTotalReachableObjectsSizeBytes() {
    return totalReachableObjectsSizeBytes;
  }

  public int getTotalReachableObjectsNumber() {
    return totalReachableObjectsNumber;
  }

  public long getTotalStrongReferencedObjectsSizeBytes() {
    return totalStrongReferencedObjectsSizeBytes;
  }

  public int getTotalStrongReferencedObjectsNumber() {
    return totalStrongReferencedObjectsNumber;
  }
}
