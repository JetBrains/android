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
import com.android.testutils.ImageDiffUtil;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link ThumbnailManager}
 */
public class ThumbnailManagerTest extends NavTestCase {

  private NavDesignSurface mySurface;

  @Override
  public void setUp() {
    super.setUp();
    TestableThumbnailManager.register(myFacet, myRootDisposable);
    mySurface = new NavDesignSurface(myFacet.getModule().getProject(), getMyRootDisposable());
  }

  public void testCaching() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    VirtualFile virtualFile = psiFile.getVirtualFile();
    NlModel model = NlModel.builder(myFacet, virtualFile, ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(virtualFile))
      .withParentDisposable(getMyRootDisposable())
      .withComponentRegistrar(mySurface.getComponentRegistrar())
      .build();
    RefinableImage imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    BufferedImage image = imageFuture.getTerminalImage();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    assertSame(image, imageFuture.getTerminalImage());

    // We should survive psi reparse
    psiFile.clearCaches();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    assertSame(image, imageFuture.getTerminalImage());

    image = imageFuture.getTerminalImage();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    assertSame(image, imageFuture.getTerminalImage());

    VirtualFile resDir = myFixture.findFileInTempDir("res");
    IdeResourcesUtil.createValueResource(getProject(), resDir, "foo", ResourceType.STRING, "strings.xml",
                                         Collections.singletonList(ResourceFolderType.VALUES.getName()), "bar");
    waitForResourceRepositoryUpdates();

    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    assertNotSame(image, imageFuture.getTerminalImage());

    image = imageFuture.getTerminalImage();
    imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    assertSame(image, imageFuture.getTerminalImage());
  }

  public void testOldVersion() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    VirtualFile virtualFile = psiFile.getVirtualFile();
    NlModel model = NlModel.builder(myFacet, virtualFile, ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(virtualFile))
      .withParentDisposable(getMyRootDisposable())
      .withComponentRegistrar(mySurface.getComponentRegistrar())
      .build();
    Configuration configuration = model.getConfiguration();
    RefinableImage thumbnail = manager.getThumbnail(psiFile, configuration, new Dimension(100, 200));
    BufferedImage orig = thumbnail.getTerminalImage();
    assertNull(thumbnail.getImage());

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

    RefinableImage image = manager.getThumbnail(psiFile, configuration, new Dimension(100, 200));
    taskStarted.acquire();
    assertFalse(image.getRefined().isDone());
    assertEquals(image.getImage(), orig);
    inProgressCheckDone.release();
    BufferedImage newVersion = image.getTerminalImage();
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
                                      @NotNull RenderService renderService) {
        started.release();
        lock.tryLock();
        renderCount.incrementAndGet();
        return ReadAction.compute(() -> RenderTestUtil.createRenderTask(facet, file.getVirtualFile(), configuration));
      }
    };
    Disposer.register(getProject(), manager);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    VirtualFile virtualFile = psiFile.getVirtualFile();
    NlModel model = NlModel.builder(myFacet, virtualFile, ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(virtualFile))
      .withParentDisposable(getMyRootDisposable())
      .withComponentRegistrar(mySurface.getComponentRegistrar())
      .build();
    RefinableImage imageFuture = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));
    RefinableImage imageFuture2 = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(100, 200));

    started.acquire();
    assertFalse(imageFuture.getRefined().isDone());
    assertFalse(imageFuture2.getRefined().isDone());
    lock.unlock();
    assertSame(imageFuture.getTerminalImage(), imageFuture2.getTerminalImage());
    assertEquals(1, renderCount.get());
  }

  private static final float MAX_PERCENT_DIFFERENT = 1f;

  public void testGeneratedImage() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);

    VirtualFile file = NavTestCase.findVirtualProjectFile(getProject(), "res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    VirtualFile virtualFile = psiFile.getVirtualFile();
    NlModel model = NlModel.builder(myFacet, virtualFile, ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(virtualFile))
      .withParentDisposable(getMyRootDisposable())
      .withComponentRegistrar(mySurface.getComponentRegistrar())
      .build();
    BufferedImage image = manager.getThumbnail(psiFile, model.getConfiguration(), new Dimension(192, 320)).getTerminalImage();

    String fileName = "basic_activity_1.png";

    if (UIUtil.isRetina() && ImageUtils.supportsRetina()) {
      image = ImageUtil.toBufferedImage(image);
      fileName = "basic_activity_1_retina.png";
    }

    File goldenFile = Paths.get(Companion.getTestDataPath(), "thumbnails", fileName).toFile();
    BufferedImage goldenImage = ImageIO.read(goldenFile);

    ImageDiffUtil.assertImageSimilar("thumbnail.png", goldenImage, image, MAX_PERCENT_DIFFERENT);
  }
}
