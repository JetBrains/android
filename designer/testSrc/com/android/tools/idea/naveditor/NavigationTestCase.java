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
package com.android.tools.idea.naveditor;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager;
import com.android.tools.idea.naveditor.scene.ThumbnailManager;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.startup.AndroidCodeStyleSettingsModifier;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Function;

import static com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC;
import static org.mockito.Mockito.when;

// TODO: in most cases this probably doesn't need to extend AndroidGradleTestCase/doesn't need to load the project.
public abstract class NavigationTestCase extends AndroidGradleTestCase {

  public static final String TAG_FRAGMENT = "fragment";
  public static final String TAG_NAVIGATION = "navigation";
  protected CodeStyleSettings mySettings;
  private boolean myUseCustomSettings;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject(NAVIGATION_EDITOR_BASIC);
    myFixture.setTestDataPath(getTestDataPath());
    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    AndroidCodeStyleSettingsModifier.modify(mySettings);
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
    myUseCustomSettings = getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS;
    getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = true;
    TestableThumbnailManager.register(myAndroidFacet);
  }

  private static AndroidXmlCodeStyleSettings getAndroidCodeStyleSettings() {
    return AndroidXmlCodeStyleSettings.getInstance(CodeStyleSchemes.getInstance().getDefaultScheme().getCodeStyleSettings());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ThumbnailManager thumbnailManager = ThumbnailManager.getInstance(myAndroidFacet);
      if (thumbnailManager instanceof TestableThumbnailManager) {
        ((TestableThumbnailManager)thumbnailManager).deregister();
      }
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = myUseCustomSettings;
      mySettings = null;
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static String getTestDataPath() {
    return getDesignerPluginHome() + "/testData";
  }

  @NotNull
  public static String getDesignerPluginHome() {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    String adtPath = PathManager.getHomePath() + "/../adt/idea/designer";
    if (new File(adtPath).exists()) {
      return adtPath;
    }
    return AndroidTestBase.getAndroidPluginHome();
  }

  @NotNull
  protected ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root) {
    Function<? super SyncNlModel, ? extends SceneManager> managerFactory = model -> {
      when(((NavDesignSurface)model.getSurface()).getSchema()).thenReturn(NavigationSchema.getOrCreateSchema(myAndroidFacet));
      when(((NavDesignSurface)model.getSurface()).getCurrentNavigation()).then(invocation -> model.getComponents().get(0));
      return new NavSceneManager(model, (NavDesignSurface)model.getSurface());
    };

    return new ModelBuilder(myAndroidFacet, myFixture, name, root, managerFactory,
                            NavSceneManager::updateHierarchy, "nav", NavDesignSurface.class,
                            (tag, model) -> DesignSurface.createComponent(tag, model));
  }

  @NotNull
  protected NavigationComponentDescriptor rootComponent() {
    return new NavigationComponentDescriptor();
  }

  @NotNull
  protected NavigationComponentDescriptor navigationComponent(@NotNull String id) {
    NavigationComponentDescriptor descriptor = new NavigationComponentDescriptor();
    descriptor.id("@id/" + id);
    return descriptor;
  }

  @NotNull
  protected FragmentComponentDescriptor fragmentComponent(@NotNull String id) {
    FragmentComponentDescriptor descriptor = new FragmentComponentDescriptor();
    descriptor.id("@id/" + id);
    return descriptor;
  }

  @NotNull
  protected ActionComponentDescriptor actionComponent(@NotNull String id) {
    ActionComponentDescriptor descriptor = new ActionComponentDescriptor();
    descriptor.id("@id/" + id);
    return descriptor;
  }

  @NotNull
  protected ActivityComponentDescriptor activityComponent(@NotNull String id) {
    ActivityComponentDescriptor descriptor = new ActivityComponentDescriptor();
    descriptor.id("@id/" + id);
    return descriptor;
  }

  protected static class NavigationComponentDescriptor extends ComponentDescriptor {
    public NavigationComponentDescriptor() {
      super(TAG_NAVIGATION);
    }

    @NotNull
    public NavigationComponentDescriptor withStartDestinationAttribute(@NotNull String startDestination) {
      withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/" + startDestination);
      return this;
    }

    @NotNull
    public NavigationComponentDescriptor withLabelAttribute(@NotNull String label) {
      withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL, label);
      return this;
    }
  }

  protected static class FragmentComponentDescriptor extends ComponentDescriptor {
    public FragmentComponentDescriptor() {
      super(TAG_FRAGMENT);
    }

    @NotNull
    public FragmentComponentDescriptor withLayoutAttribute(@NotNull String layout) {
      withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/" + layout);
      return this;
    }
  }

  protected static class ActionComponentDescriptor extends ComponentDescriptor {
    public ActionComponentDescriptor() {
      super(NavigationSchema.TAG_ACTION);
    }

    @NotNull
    public ActionComponentDescriptor withDestinationAttribute(@NotNull String destination) {
      withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/" + destination);
      return this;
    }
  }

  protected static class ActivityComponentDescriptor extends ComponentDescriptor {
    public ActivityComponentDescriptor() {
      super("activity");
    }
  }
}
