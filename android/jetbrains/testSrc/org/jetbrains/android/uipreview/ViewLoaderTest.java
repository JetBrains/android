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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.AndroidBuildTargetReference;
import com.android.tools.idea.rendering.AndroidFacetRenderModelModule;
import com.android.tools.idea.rendering.StudioModuleRenderContext;
import com.android.tools.idea.res.StudioResourceIdManager;
import com.android.tools.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.rendering.StudioRenderServiceKt;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.rendering.ViewLoader;
import com.android.tools.rendering.classloading.ModuleClassLoaderManager;
import com.android.tools.res.ids.ResourceIdManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;

public class ViewLoaderTest extends AndroidTestCase {

  private AndroidBuildTargetReference myBuildTarget;
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
  ModuleClassLoaderManager.Reference<StudioModuleClassLoader> myClassLoaderReference;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuildTarget = AndroidBuildTargetReference.gradleOnly(myFacet);
    RenderTestUtil.beforeRenderTestCase();
    Module module = myFacet.getModule();
    AndroidPlatform platform = AndroidPlatforms.getInstance(module);
    assertNotNull(platform);
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(module);
    myLayoutLib = StudioRenderServiceKt.getLayoutLibrary(module, StudioEmbeddedRenderTarget.getCompatibilityTarget(manager.getHighestApiTarget()));
    assertNotNull(myLayoutLib);
    myClassLoaderReference = StudioModuleClassLoaderManager.get().getShared(myLayoutLib.getClassLoader(), StudioModuleRenderContext.forModule(myModule));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      // Copy the classloader field before super.tearDown() nulls it out via UsefulTestCase.clearDeclaredFields().
      ModuleClassLoaderManager.Reference<StudioModuleClassLoader> classLoaderReference = myClassLoaderReference;
      super.tearDown();
      StudioModuleClassLoaderManager.get().release(classLoaderReference);
    }
  }

  public void testMissingClass() throws Exception {
    Project project = myModule.getProject();
    RenderLogger logger = new RenderLogger();
    ViewLoader viewLoader = new ViewLoader(myLayoutLib, new AndroidFacetRenderModelModule(myBuildTarget), logger, null,
                                           myClassLoaderReference.getClassLoader());

    assertNull(viewLoader.loadClass("broken.brokenclass", true));
    assertTrue(logger.hasErrors());
    assertThat(logger.getMissingClasses(), hasItem("broken.brokenclass"));

    logger = new RenderLogger();
    viewLoader = new ViewLoader(myLayoutLib, new AndroidFacetRenderModelModule(myBuildTarget), logger, null, myClassLoaderReference.getClassLoader());

    try {
      viewLoader.loadView("broken.brokenclass", null, null);
      fail("ClassNotFoundException expected");
    }
    catch (ClassNotFoundException ignored) {
    }

    logger = new RenderLogger();
    viewLoader = new ViewLoader(myLayoutLib, new AndroidFacetRenderModelModule(myBuildTarget), logger, null, myClassLoaderReference.getClassLoader());
    assertNull(viewLoader.loadClass("broken.brokenclass", false));
    assertFalse(logger.hasErrors());
  }

  public void testRClassLoad() throws ClassNotFoundException {
    RenderLogger logger = new RenderLogger();
    ViewLoader viewLoader = new ViewLoader(myLayoutLib, new AndroidFacetRenderModelModule(myBuildTarget), logger, null, myClassLoaderReference.getClassLoader());
    ResourceIdManager idManager = StudioResourceIdManager.get(myModule);
    assertNotNull(idManager);

    // No LocalResourceRepository exists prior to calling loadAndParseRClass. It will get created during the call.
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNull(facet != null ? StudioResourceRepositoryManager.getInstance(facet).getCachedAppResources() : null);
    idManager.resetCompiledIds(
      (ResourceIdManager.RClassParser rClassParser) -> {
        try {
          viewLoader.loadAndParseRClass("org.jetbrains.android.uipreview.ViewLoaderTest$R", rClassParser);
        } catch (ClassNotFoundException ignored) { }
      }
    );

    assertEquals(0x7f0a000e, idManager.getCompiledId(new ResourceReference(RES_AUTO, ResourceType.STRING, "app_name")).intValue());
    // This value wasn't read from the R class since it wasn't a const.
    assertNull(idManager.getCompiledId(new ResourceReference(RES_AUTO, ResourceType.STRING, "not_const")));

    // 7f is the app package, 14 is STRING ordinal and fffe is the first dynamic ID assigned.
    assertEquals(0x7f_14_fffe, idManager.getOrGenerateId(new ResourceReference(RES_AUTO, ResourceType.STRING, "not_const")));
  }

  public void testGetShortClassName() {
    assertEquals("", ViewLoader.getShortClassName(""));
    assertEquals("mypackage.View", ViewLoader.getShortClassName("mypackage.View"));
    assertEquals("android...View", ViewLoader.getShortClassName("android.test.package.View"));
    assertEquals("mypackage.test...View", ViewLoader.getShortClassName("mypackage.test.package.View"));
  }
}
