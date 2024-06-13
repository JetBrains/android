/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.intellij.openapi.application.ApplicationManager;
import org.junit.rules.ExternalResource;

/**
 * Rule for unit tests which perform rendering; this rule will make sure that the render threads are correctly
 * cleaned up after the test.
 */
public class RenderTestRule extends ExternalResource {

  @Override
  protected void before() throws Throwable {
    super.before();

    RenderTestUtil.beforeRenderTestCase();
  }

  @Override
  protected void after() {
    try {
      ApplicationManager.getApplication().invokeAndWait(RenderTestUtil::afterRenderTestCase);
    }
    finally {
      super.after();
    }
  }
}
