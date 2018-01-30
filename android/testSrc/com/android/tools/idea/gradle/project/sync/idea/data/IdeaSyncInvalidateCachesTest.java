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
package com.android.tools.idea.gradle.project.sync.idea.data;

import com.android.tools.idea.testing.AndroidGradleTestCase;

public class IdeaSyncInvalidateCachesTest extends AndroidGradleTestCase {

  public void testCacheIsInvalidated() throws Exception {
    loadSimpleApplication();
    DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(getProject());

    // All models are in cache
    assertFalse(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));

    // After erasing, models are not in the cache anymore
    dataNodeCaches.clearCaches();
    assertTrue(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));

    // Sync and check if models are replaced
    requestSyncAndWait();
    assertFalse(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));
  }
}
