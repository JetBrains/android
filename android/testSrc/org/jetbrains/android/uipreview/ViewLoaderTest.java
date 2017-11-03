/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTestBase;
import com.android.tools.idea.res.AppResourceRepository;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.sdk.AndroidPlatform;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

public class ViewLoaderTest extends RenderTestBase {
  @SuppressWarnings("ALL")
  public static class R {
    public static final class string {
      public static final int app_name = 0x7f0a000e;
      public final int not_final = 0x7f0a0050;
    }

    public static final class not_a_type {
      public static final int test = 0x7f0a0111;
    }
  }

  LayoutLibrary myLayoutLib;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Module module = myFacet.getModule();
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    assertNotNull(platform);
    myLayoutLib = RenderService.getLayoutLibrary(module, ConfigurationManager.create(module).getHighestApiTarget());
    assertNotNull(myLayoutLib);
  }

  public void testMissingClass() throws Exception {
    RenderLogger logger = RenderService.getInstance(myFacet).createLogger();
    ViewLoader viewLoader = new ViewLoader(myLayoutLib, myFacet, logger, null);

    assertNull(viewLoader.loadClass("broken.brokenclass", true));
    assertTrue(logger.hasErrors());
    assertThat(logger.getMissingClasses(), hasItem("broken.brokenclass"));

    logger = RenderService.getInstance(myFacet).createLogger();
    viewLoader = new ViewLoader(myLayoutLib, myFacet, logger, null);

    try {
      viewLoader.loadView("broken.brokenclass", null, null);
      fail("ClassNotFoundException expected");
    }
    catch (ClassNotFoundException ignored) {
    }

    logger = RenderService.getInstance(myFacet).createLogger();
    viewLoader = new ViewLoader(myLayoutLib, myFacet, logger, null);
    assertNull(viewLoader.loadClass("broken.brokenclass", false));
    assertFalse(logger.hasErrors());
  }

  public void testRClassLoad() throws ClassNotFoundException {
    RenderLogger logger = RenderService.getInstance(myFacet).createLogger();
    ViewLoader viewLoader = new ViewLoader(myLayoutLib, myFacet, logger, null);

    // No AppResourceRepository exists prior to calling loadAndParseRClass. It will get created during the call.
    assertNull(AppResourceRepository.findExistingInstance(myModule));
    viewLoader.loadAndParseRClass("org.jetbrains.android.uipreview.ViewLoaderTest$R");

    AppResourceRepository appResources = AppResourceRepository.findExistingInstance(myModule);
    assertNotNull(appResources);

    assertEquals(0x7f0a000e, appResources.getResourceId(ResourceType.STRING, "app_name").intValue());
    // This value wasn't read from the R class since it wasn't final. The value must be a dynamic ID (they start at 0x7fff0000)
    assertEquals(0x7fff0001, appResources.getResourceId(ResourceType.STRING, "not_final").intValue());
  }

  public void testGetShortClassName() {
    assertEquals("", ViewLoader.getShortClassName(""));
    assertEquals("mypackage.View", ViewLoader.getShortClassName("mypackage.View"));
    assertEquals("android...View", ViewLoader.getShortClassName("android.test.package.View"));
    assertEquals("mypackage.test...View", ViewLoader.getShortClassName("mypackage.test.package.View"));
  }
}