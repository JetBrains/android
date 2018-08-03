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
package com.android.tools.idea.naveditor.scene;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tests for {@link ThumbnailManager}
 */
public class ThumbnailManagerTest extends NavTestCase {
  @Override
  public void setUp() {
    super.setUp();
    TestableThumbnailManager.register(myFacet, getProject());
  }

  public void testCaching() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    NlModel model = NlModel.create(getProject(), myFacet, psiFile.getVirtualFile());
    CompletableFuture<BufferedImage> imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    BufferedImage image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    assertSame(image, imageFuture.get());

    // We should survive psi reparse
    psiFile.clearCaches();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    assertSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    assertSame(image, imageFuture.get());

    VirtualFile resDir = myFixture.findFileInTempDir("res");
    AndroidResourceUtil.createValueResource(getProject(), resDir, "foo", ResourceType.STRING, "strings.xml",
                                            Collections.singletonList(ResourceFolderType.VALUES.getName()), "bar");
    ResourceRepositoryManager.getAppResources(myFacet).sync();

    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    assertNotSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    assertSame(image, imageFuture.get());
  }
  
  public void testOldVersion() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    NlModel model = NlModel.create(getProject(), myFacet, psiFile.getVirtualFile());
    Configuration configuration = model.getConfiguration();
    BufferedImage orig = manager.getThumbnail(psiFile, configuration).get();
    assertNull(manager.getOldThumbnail(file, configuration));

    Semaphore inProgressCheckDone = new Semaphore(1);
    inProgressCheckDone.acquire();
    Semaphore taskStarted = new Semaphore(1);
    taskStarted.acquire();

    RenderService.setForTesting(getProject(), new RenderService(getProject()) {
      @NotNull
      @Override
      public RenderTaskBuilder taskBuilder(@NotNull AndroidFacet facet, @NotNull Configuration configuration) {
        try {
          taskStarted.release();
          inProgressCheckDone.acquire();
        }
        catch (Exception e) {
          fail(e.getMessage());
        }
        return super.taskBuilder(facet, configuration);
      }
    });

    ((VirtualFileSystemEntry)file).setTimeStamp(file.getTimeStamp() + 100);

    CompletableFuture<BufferedImage> newFuture = manager.getThumbnail(psiFile, configuration);
    taskStarted.acquire();
    assertFalse(newFuture.isDone());
    assertEquals(manager.getOldThumbnail(file, configuration), orig);
    inProgressCheckDone.release();
    BufferedImage newVersion = newFuture.get();
    assertNotSame(orig, newVersion);
    assertNotNull(newVersion);
  }

  public void testSimultaneousRequests() throws Exception {
    Lock lock = new ReentrantLock();
    lock.lock();
    Semaphore started = new Semaphore(0);
    AtomicInteger renderCount = new AtomicInteger();
    ThumbnailManager manager = new ThumbnailManager(myFacet) {
      @Nullable
      @Override
      protected RenderTask createTask(@NotNull AndroidFacet facet,
                                      @NotNull XmlFile file,
                                      @NotNull Configuration configuration,
                                      RenderService renderService) {
        started.release();
        lock.tryLock();
        renderCount.incrementAndGet();
        return ReadAction.compute(() -> RenderTestUtil.createRenderTask(facet, file.getVirtualFile(), configuration));
      }
    };
    Disposer.register(getProject(), manager);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    NlModel model = NlModel.create(getProject(), myFacet, psiFile.getVirtualFile());
    CompletableFuture<BufferedImage> imageFuture = manager.getThumbnail(psiFile, model.getConfiguration());
    CompletableFuture<BufferedImage> imageFuture2 = manager.getThumbnail(psiFile, model.getConfiguration());

    started.acquire();
    assertFalse(imageFuture.isDone());
    assertFalse(imageFuture2.isDone());
    lock.unlock();
    assertSame(imageFuture.get(), imageFuture2.get());
    assertEquals(1, renderCount.get());
  }
}
