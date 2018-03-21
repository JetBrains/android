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
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.rendering.ImagePool;
import com.android.tools.idea.res.AppResourceRepository;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.util.AndroidResourceUtil;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ThumbnailManager}
 */
public class ThumbnailManagerTest extends NavTestCase {
  @Override
  public void setUp() {
    super.setUp();
    TestableThumbnailManager.register(myFacet);
  }

  @Override
  protected void tearDown() {
    try {
      ((TestableThumbnailManager)ThumbnailManager.getInstance(myFacet)).deregister();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCaching() throws Exception {
    ThumbnailManager manager = ThumbnailManager.getInstance(myFacet);
    VirtualFile file = myFixture.findFileInTempDir("res/layout/activity_main.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);

    DesignSurface surface = mock(NavDesignSurface.class);
    NlModel model = NlModel.create(getMyRootDisposable(), myFacet, psiFile.getVirtualFile());
    CompletableFuture<ImagePool.Image> imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    ImagePool.Image image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());

    psiFile.clearCaches();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertNotSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());

    VirtualFile resDir = myFixture.findFileInTempDir("res");
    AndroidResourceUtil.createValueResource(getProject(), resDir, "foo", ResourceType.STRING, "strings.xml",
                                            Collections.singletonList(ResourceFolderType.VALUES.getName()), "bar");
    AppResourceRepository.getOrCreateInstance(myFacet).sync();

    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertNotSame(image, imageFuture.get());

    image = imageFuture.get();
    imageFuture = manager.getThumbnail(psiFile, surface, model.getConfiguration());
    assertSame(image, imageFuture.get());
  }
}
