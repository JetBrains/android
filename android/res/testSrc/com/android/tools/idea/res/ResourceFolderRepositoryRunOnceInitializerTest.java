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
package com.android.tools.idea.res;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.utils.sleep.ThreadSleeper;
import com.intellij.openapi.application.ApplicationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceFolderRepositoryRunOnceInitializerTest {
  @Rule
  public final AndroidProjectRule androidProjectRule = AndroidProjectRule.inMemory();

  @Test
  public void runOnceInitializer_runsOnlyOnce() throws Exception {
    int totalThreads = 20;

    CountDownLatch threadStarted = new CountDownLatch(totalThreads);
    CountDownLatch threadEnded = new CountDownLatch(totalThreads);

    CountDownLatch initializerLockEntered = new CountDownLatch(1);

    CountDownLatch advancePoint1 = new CountDownLatch(1);
    CountDownLatch advancePoint2 = new CountDownLatch(1);

    AtomicInteger initializerRunCount = new AtomicInteger();
    ResourceFolderRepository.RunOnceInitializer initializer =
      new ResourceFolderRepository.RunOnceInitializer(() -> {
        try {
          initializerLockEntered.countDown();
          assertThat(advancePoint2.await(10, SECONDS)).isTrue();
          initializerRunCount.incrementAndGet();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });

    Runnable initializerWithReadLockThread = () -> {
      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          threadStarted.countDown();
          assertThat(advancePoint1.await(10, SECONDS)).isTrue();

          initializer.run();

          threadEnded.countDown();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
    };

    Runnable initializerWithoutReadLockThread = () -> {
      try {
        threadStarted.countDown();
        assertThat(advancePoint1.await(10, SECONDS)).isTrue();

        initializer.run();

        threadEnded.countDown();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    };

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < totalThreads; i += 2) {
      Thread t1 = new Thread(initializerWithReadLockThread);
      Thread t2 = new Thread(initializerWithoutReadLockThread);
      t1.start();
      t2.start();
      threads.add(t1);
      threads.add(t2);
    }

    // Wait for all threads to start
    assertThat(threadStarted.await(10, SECONDS)).isTrue();

    // Allow threads to proceed
    advancePoint1.countDown();

    // Wait for one thread to enter the initializer
    assertThat(initializerLockEntered.await(10, SECONDS)).isTrue();

    // Allow threads to proceed
    advancePoint2.countDown();

    // Wait for all threads to finish
    assertThat(threadEnded.await(10, SECONDS)).isTrue();
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(initializerRunCount.get()).isEqualTo(1);
  }

  @Test
  public void runOnceWithReadLockInitializer_runsOnlyOnce() throws Exception {
    int totalThreads = 20;

    CountDownLatch threadStarted = new CountDownLatch(totalThreads);
    CountDownLatch threadEnded = new CountDownLatch(totalThreads);

    CountDownLatch initializerLockEntered = new CountDownLatch(1);

    CountDownLatch advancePoint1 = new CountDownLatch(1);
    CountDownLatch advancePoint2 = new CountDownLatch(1);

    AtomicInteger initializerRunCount = new AtomicInteger();
    ResourceFolderRepository.RunOnceWithReadLockInitializer initializer =
      new ResourceFolderRepository.RunOnceWithReadLockInitializer(() -> {
        try {
          initializerLockEntered.countDown();
          assertThat(advancePoint2.await(10, SECONDS)).isTrue();
          initializerRunCount.incrementAndGet();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });

    Runnable initializerWithReadLockThread = () -> {
      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          threadStarted.countDown();
          assertThat(advancePoint1.await(10, SECONDS)).isTrue();

          initializer.run();

          threadEnded.countDown();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
    };

    Runnable initializerWithoutReadLockThread = () -> {
      try {
        threadStarted.countDown();
        assertThat(advancePoint1.await(10, SECONDS)).isTrue();

        initializer.run();

        threadEnded.countDown();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    };

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < totalThreads; i += 2) {
      Thread t1 = new Thread(initializerWithReadLockThread);
      Thread t2 = new Thread(initializerWithoutReadLockThread);
      t1.start();
      t2.start();
      threads.add(t1);
      threads.add(t2);
    }

    // Wait for all threads to start
    assertThat(threadStarted.await(10, SECONDS)).isTrue();

    // Allow threads to proceed
    advancePoint1.countDown();

    // Wait for one thread to enter the initializer
    assertThat(initializerLockEntered.await(10, SECONDS)).isTrue();

    // Allow threads to proceed
    advancePoint2.countDown();

    // Wait for all threads to finish
    assertThat(threadEnded.await(10, SECONDS)).isTrue();
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(initializerRunCount.get()).isEqualTo(1);
  }

  @Test
  public void runOnceWithReadLockInitializer_writeThreadBlocks() throws Exception {
    AtomicInteger initializerRunCount = new AtomicInteger();
    ResourceFolderRepository.RunOnceWithReadLockInitializer initializer =
      new ResourceFolderRepository.RunOnceWithReadLockInitializer(initializerRunCount::incrementAndGet);

    CountDownLatch backgroundThreadWithLongReadStarted = new CountDownLatch(1);
    CountDownLatch advanceBackgroundThreadWithLongRead = new CountDownLatch(1);
    CountDownLatch backgroundThreadWithLongReadFinished = new CountDownLatch(1);
    Thread backgroundThreadWithLongRead = new Thread(() -> {
      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          backgroundThreadWithLongReadStarted.countDown();
          assertThat(advanceBackgroundThreadWithLongRead.await(10, SECONDS)).isTrue();

          initializer.run();

          backgroundThreadWithLongReadFinished.countDown();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
    });

    CountDownLatch backgroundThreadWithoutReadStarted = new CountDownLatch(1);
    CountDownLatch advanceBackgroundThreadWithoutRead = new CountDownLatch(1);
    CountDownLatch backgroundThreadWithoutReadFinished = new CountDownLatch(1);
    Thread backgroundThreadWithoutRead = new Thread(() -> {
      try {
        backgroundThreadWithoutReadStarted.countDown();
        assertThat(advanceBackgroundThreadWithoutRead.await(10, SECONDS)).isTrue();

        initializer.run();

        backgroundThreadWithoutReadFinished.countDown();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });

    CountDownLatch writeThreadStarted = new CountDownLatch(1);
    CountDownLatch writeThreadActionEntered = new CountDownLatch(1);
    CountDownLatch advanceWriteAction = new CountDownLatch(1);
    CountDownLatch writeThreadFinished = new CountDownLatch(1);
    Thread writeThread = new Thread(() -> {
      writeThreadStarted.countDown();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            writeThreadActionEntered.countDown();
            assertThat(advanceWriteAction.await(10, SECONDS)).isTrue();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
      });

      writeThreadFinished.countDown();
    });

    // Start the background thread with a long read first.
    backgroundThreadWithLongRead.start();
    assertThat(backgroundThreadWithLongReadStarted.await(10, SECONDS)).isTrue();

    // Start the write thread. It should not be able to enter the write lock.
    writeThread.start();
    assertThat(writeThreadStarted.await(10, SECONDS)).isTrue();
    ThreadSleeper.INSTANCE.sleep(500);
    assertThat(writeThreadActionEntered.getCount()).isEqualTo(1);

    // Start the background thread without a read. Advance it until it tries to initialize.
    backgroundThreadWithoutRead.start();
    assertThat(backgroundThreadWithoutReadStarted.await(10, SECONDS)).isTrue();
    advanceBackgroundThreadWithoutRead.countDown();
    ThreadSleeper.INSTANCE.sleep(500);

    // At this point, the background thread without a read should be blocked, and nothing should be initialized.
    // The write thread should still not have entered the lock.
    assertThat(initializerRunCount.get()).isEqualTo(0);
    assertThat(writeThreadActionEntered.getCount()).isEqualTo(1);

    // Now allow the background thread with a lock to proceed, and wait for it to finish. This should do initialization.
    advanceBackgroundThreadWithLongRead.countDown();
    assertThat(backgroundThreadWithLongReadFinished.await(10, SECONDS)).isTrue();
    backgroundThreadWithLongRead.join();
    assertThat(initializerRunCount.get()).isEqualTo(1);

    // Since the background thread with a read has finished, the write thread can proceed. The background thread
    // without a read lock should still be blocked.
    assertThat(writeThreadActionEntered.await(10, SECONDS)).isTrue();
    assertThat(backgroundThreadWithoutReadFinished.getCount()).isEqualTo(1);

    // Advance the write thread, allowing it to finish.
    advanceWriteAction.countDown();
    assertThat(writeThreadFinished.await(10, SECONDS)).isTrue();
    writeThread.join();

    // The background thread without a lock can now complete.
    assertThat(backgroundThreadWithoutReadFinished.await(10, SECONDS)).isTrue();
    backgroundThreadWithoutRead.join();

    // Ensure we only initialized once.
    assertThat(initializerRunCount.get()).isEqualTo(1);
  }
}
