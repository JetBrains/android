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

import org.junit.Assert;
import org.junit.Test;

public class LightweightHeapTraverseTest {
  @Test
  public void testTotalAndStrongReferencedMemory() {
    LightweightTraverseResult result = LightweightHeapTraverse.collectReport(new LightweightHeapTraverseConfig(true, true, true));
    Assert.assertTrue(result.getTotalObjectsSizeBytes() > 0);
    Assert.assertTrue(result.getTotalObjectsNumber() > 0);

    Assert.assertTrue(result.getTotalReachableObjectsNumber() > 0);
    Assert.assertTrue(result.getTotalReachableObjectsSizeBytes() > 0);

    Assert.assertTrue(result.getTotalStrongReferencedObjectsSizeBytes() > 0);
    Assert.assertTrue(result.getTotalStrongReferencedObjectsNumber() > 0);

    Assert.assertTrue(result.getTotalReachableObjectsSizeBytes() < result.getTotalObjectsSizeBytes());
    Assert.assertTrue(result.getTotalReachableObjectsNumber() < result.getTotalObjectsNumber());

    Assert.assertTrue(result.getTotalStrongReferencedObjectsSizeBytes() < result.getTotalReachableObjectsSizeBytes());
    Assert.assertTrue(result.getTotalStrongReferencedObjectsNumber() < result.getTotalReachableObjectsNumber());
  }

  @Test
  public void testStrongReferencedMemoryCollectionDisabled() {
    LightweightTraverseResult result = LightweightHeapTraverse.collectReport(new LightweightHeapTraverseConfig(true, true, false));
    Assert.assertTrue(result.getTotalObjectsSizeBytes() > 0);
    Assert.assertTrue(result.getTotalObjectsNumber() > 0);

    Assert.assertTrue(result.getTotalObjectsSizeBytes() > 0);
    Assert.assertTrue(result.getTotalObjectsNumber() > 0);

    Assert.assertEquals(0, result.getTotalStrongReferencedObjectsSizeBytes());
    Assert.assertEquals(0, result.getTotalStrongReferencedObjectsNumber());
  }
}
