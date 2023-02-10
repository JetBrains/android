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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ViewInfo;
import java.util.concurrent.CompletableFuture;
import junit.framework.TestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderServiceTest extends TestCase {
  public void testGetSafeBounds() {
    ViewInfo valid;
    ViewInfo invalid;

    valid = new ViewInfo("", "", 0, 0, 0, 0);
    assertSame(valid, RenderService.getSafeBounds(valid));

    valid = new ViewInfo("", "", 0, 0, 1024, 768);
    assertSame(valid, RenderService.getSafeBounds(valid));

    valid = new ViewInfo("", "", -200, -200, 8000, 8000);
    assertSame(valid, RenderService.getSafeBounds(valid));


    invalid = new ViewInfo("", "", -(1 << 27), 0, 0, 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));
    assertEquals(0, RenderService.getSafeBounds(invalid).getLeft());

    invalid = new ViewInfo("", "", +(1 << 27), 0, 0, 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, -(1 << 27), 0, 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, +(1 << 27), 0, 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, 0, -(1 << 27), 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, 0, +(1 << 27), 0);
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, 0, 0, -(1 << 27));
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));

    invalid = new ViewInfo("", "", 0, 0, 0, +(1 << 27));
    assertNotSame(invalid, RenderService.getSafeBounds(invalid));
  }

  public void testAsyncRenderAction() throws ExecutionException, InterruptedException {
    AtomicBoolean called = new AtomicBoolean(false);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    RenderAsyncActionExecutor renderActionExecutor = RenderService.getRenderAsyncActionExecutor();
    long renderActionCounter = renderActionExecutor.getExecutedRenderActionCount();
    CompletableFuture<Void> future = renderActionExecutor.runAsyncAction(() -> {
      try {
        countDownLatch.await();
      }
      catch (InterruptedException ignore) {
      }
      called.set(true);

      return null;
    });

    assertFalse(called.get());
    countDownLatch.countDown();
    future.get();
    assertEquals(renderActionCounter + 1, renderActionExecutor.getExecutedRenderActionCount());
    assertTrue(called.get());
  }
}