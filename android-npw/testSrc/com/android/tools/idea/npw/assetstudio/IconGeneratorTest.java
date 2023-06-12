/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertThrows;
import static java.lang.Thread.sleep;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.npw.assetstudio.IconGenerator.IconOptions;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ThrowableRunnable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link IconGenerator}.
 */
@RunWith(JUnit4.class)
public final class IconGeneratorTest {
  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();

  @Test
  public void generateIconsIsCancelledWhenDisposed() {
    CountDownLatch latch = new CountDownLatch(1);

    IconGenerator iconGenerator = new IconGenerator(myProjectRule.getProject(), 1, new GraphicGeneratorContext(1)) {
      @NotNull
      @Override
      public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) {
        throw new RuntimeException("Should not be called");
      }

      @NotNull
      @Override
      public IconOptions createOptions(boolean forPreview) {
        throw new RuntimeException("Should not be called");
      }

      @NotNull
      @Override
      protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context,
                                                                        @NotNull IconOptions options,
                                                                        @NotNull String name) {
        List<Callable<GeneratedIcon>> tasks = new ArrayList<>();
        tasks.add(() -> {
          latch.countDown(); // Broadcast that we are now starting to execute a task
          try {
            sleep(1_000); // Simulate a slow task
          }
          catch (InterruptedException ignored) {
          }
          return new GeneratedXmlResource("name", new PathString(""), IconCategory.REGULAR, "xmlText");
        });

        return tasks;
      }
    };

    Disposer.register(myProjectRule.getFixture().getTestRootDisposable(), iconGenerator);

    new Thread(() -> {
      try {
        latch.await(1, TimeUnit.SECONDS); // Wait for the task to start.
      }
      catch (InterruptedException ignored) {
      }
      assertThat(latch.getCount()).isEqualTo(0);
      Disposer.dispose(iconGenerator);
    }).start();

    assertThrows(
      CancellationException.class,
      (ThrowableRunnable<Throwable>)() -> iconGenerator.generateIcons(new IconOptions(true))
    );
  }

  @Test
  public void generateIntoFileMap() {
    VectorIconGenerator generator = new VectorIconGenerator(myProjectRule.getProject(), 24);
    generator.sourceAsset().setValue(new VectorAsset());
    generator.outputName().set("foo");
    Set<File> files = generator.generateIntoFileMap(new File("/app/main/res")).keySet();
    assertThat(files).containsExactly(new File("/app/main/res/drawable/foo.xml"));
  }
}
