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
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.rendering.ImagePool;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.util.AndroidResourceUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ThumbnailManager}
 */
public class ThumbnailManagerTest extends NavigationTestCase {
  private static final float MAX_PERCENT_DIFFERENT = 6.5f;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TestableThumbnailManager.register(myAndroidFacet);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((TestableThumbnailManager)ThumbnailManager.getInstance(myAndroidFacet)).deregister();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCaching() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myAndroidFacet);
    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    DesignSurface surface = mock(NavDesignSurface.class);
    NlModel model = NlModel.create(surface, getTestRootDisposable(), myAndroidFacet, psiFile);
    CompletableFuture<ImagePool.Image> imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    ImagePool.Image image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());

    ((PsiFileImpl)psiFile).clearCaches();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertNotSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());

    VirtualFile resDir = getProject().getBaseDir().findFileByRelativePath("app/src/main/res");
    AndroidResourceUtil.createValueResource(getProject(), resDir, "foo", ResourceType.STRING, "strings.xml",
                                            Collections.singletonList(ResourceFolderType.VALUES.getName()), "bar");
    AppResourceRepository.getOrCreateInstance(myAndroidFacet).sync();

    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertNotSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());
  }

  public void testGeneratedImage() throws Exception {
    File goldenFile = new File(getTestDataPath() + "/naveditor/thumbnails/basic_activity_1.png");
    BufferedImage goldenImage = ImageIO.read(goldenFile);

    ThumbnailManager manager = ThumbnailManager.getInstance(myAndroidFacet);

    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    DesignSurface surface = mock(NavDesignSurface.class);
    NlModel model = NlModel.create(surface, getTestRootDisposable(), myAndroidFacet, psiFile);
    CompletableFuture<ImagePool.Image> imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    ImagePool.Image image = imageFuture.get();
    ImageDiffUtil.assertImageSimilar("thumbnail.png", goldenImage, image.getCopy(), MAX_PERCENT_DIFFERENT);
  }
}
