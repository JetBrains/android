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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.google.common.collect.ListMultimap;
import junit.framework.TestCase;

/**
 * Test for {@link PredefinedSampleDataResourceRepository}.
 */
public class PredefinedSampleDataResourceRepositoryTest extends TestCase {
  public void testPredefinedSampleResources() {
    PredefinedSampleDataResourceRepository repo = PredefinedSampleDataResourceRepository.getInstance();

    ListMultimap<String, ResourceItem> resources = repo.getResources(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA);
    assertFalse(resources.isEmpty());
    // Check that none of the items are empty or fail.
    assertFalse(resources.values().stream().anyMatch(item -> item.getResourceValue().getValue().isEmpty()));
  }
}
