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

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceIdManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidPlatform;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

public class ViewLoaderTest extends AndroidTestCase {
  @SuppressWarnings("ALL")
  public static class R {
    public static final class string {
      public static final int app_name = 0x7f0a000e;
      public final int not_const = 0x7f0a0050;
    }

    public static final class not_a_type {
      public static final int test = 0x7f0a0111;
    }
  }

  LayoutLibrary myLayoutLib;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    RenderTestUtil.beforeRenderTestCase();
    Module module = myFacet.getModule();
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    assertNotNull(platform);
    myLayoutLib = RenderService.getLayoutLibrary(module, ConfigurationManager.create(module).getHighestApiTarget());
    assertNotNull(myLayoutLib);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
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

    ResourceIdManager idManager = ResourceIdManager.get(myModule);
    assertNotNull(idManager);

    assertEquals(0x7f0a000e, idManager.getCompiledId(new ResourceReference(RES_AUTO, ResourceType.STRING, "app_name")).intValue());
    // This value wasn't read from the R class since it wasn't a const.
    assertNull(idManager.getCompiledId(new ResourceReference(RES_AUTO, ResourceType.STRING, "not_const")));
    assertEquals(0x7f_15_ffff, idManager.getOrGenerateId(new ResourceReference(RES_AUTO, ResourceType.STRING, "not_const")));
  }

  public void testGetShortClassName() {
    assertEquals("", ViewLoader.getShortClassName(""));
    assertEquals("mypackage.View", ViewLoader.getShortClassName("mypackage.View"));
    assertEquals("android...View", ViewLoader.getShortClassName("android.test.package.View"));
    assertEquals("mypackage.test...View", ViewLoader.getShortClassName("mypackage.test.package.View"));
  }
}
