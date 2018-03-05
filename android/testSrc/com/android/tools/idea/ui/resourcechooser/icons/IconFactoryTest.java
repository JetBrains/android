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
package com.android.tools.idea.ui.resourcechooser.icons;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import org.gradle.internal.impldep.org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

public class IconFactoryTest extends AndroidTestCase {

  private VirtualFile myDrawableFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();

    @Language("XML")
    final String drawableContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                   "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                   "    android:shape=\"rectangle\"\n" +
                                   "    android:tint=\"#FF0000\">\n" +
                                   "</shape>";
    myDrawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                                                drawableContent).getVirtualFile();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
  }

  public void testIconFromPath() {
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, myDrawableFile);
    RenderTask task = RenderTestUtil.createRenderTask(myModule, myDrawableFile, configuration);
    Supplier<RenderTask> taskSupplier = () -> task;

    IconFactory factory = new IconFactory(taskSupplier);
    // createIconFromPath does not work with drawables only from images
    assertNull(factory.createIconFromPath(50, 8, false, myDrawableFile.getPath()));
    task.dispose();
  }

  public void testIconFromFromDrawable() throws InterruptedException {
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, myDrawableFile);
    RenderTask task = RenderTestUtil.createRenderTask(myModule, myDrawableFile, configuration);
    Supplier<RenderTask> taskSupplier = () -> task;

    IconFactory factory = new IconFactory(taskSupplier);
    ResourceValue value = new ResourceValue(new ResourceReference(ResourceNamespace.TODO,
                                                                  ResourceType.DRAWABLE,
                                                                  "src"), "@drawable/test");
    CountDownLatch latch = new CountDownLatch(1);
    int iconSize = 50;
    Icon placeHolder = EmptyIcon.create(iconSize);
    Icon icon = factory.createAsyncIconFromResourceValue(iconSize, 8, false,
                                             value, placeHolder, latch::countDown);
    latch.await();

    // Compare the placeholder and the rendered icon. They should be different
    BufferedImage placeHolderImage = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
    BufferedImage renderedImage = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);

    placeHolder.paintIcon(null, placeHolderImage.getGraphics(), 0, 0);
    icon.paintIcon(null, renderedImage.getGraphics(), 0, 0);

    int difference = 0;
    for (int i = 0; i < iconSize * iconSize; i++) {
      int phPixel = placeHolderImage.getRGB(i % iconSize, i / iconSize);
      int pixel = renderedImage.getRGB(i % iconSize, i / iconSize);

      if (phPixel != pixel) {
        difference++;
      }
    }
    assertTrue(difference > 100);

    task.dispose();
  }
}
