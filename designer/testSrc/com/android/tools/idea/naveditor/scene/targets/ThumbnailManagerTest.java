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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.scene.ThumbnailManager;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.rendering.ImagePool;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ThumbnailManager}
 */
public class ThumbnailManagerTest extends NavTestCase {
  private static final float MAX_PERCENT_DIFFERENT = 6.5f;

  public void testGeneratedImage() throws Exception {
    File goldenFile = new File(Companion.getTestDataPath() + "/naveditor/thumbnails/basic_activity_1.png");
    BufferedImage goldenImage = ImageIO.read(goldenFile);

    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);

    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("../unitTest/res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    DesignSurface surface = mock(NavDesignSurface.class);
    NlModel model = NlModel.create(null, myFacet, psiFile.getVirtualFile());
    CompletableFuture<ImagePool.Image> imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    ImagePool.Image image = imageFuture.get();
    ImageDiffUtil.assertImageSimilar("thumbnail.png", goldenImage, image.getCopy(), MAX_PERCENT_DIFFERENT);
    Disposer.dispose(model);
  }
}
