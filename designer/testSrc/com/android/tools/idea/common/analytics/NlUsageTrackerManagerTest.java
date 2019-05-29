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
package com.android.tools.idea.common.analytics;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.mock.MockModule;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static com.android.SdkConstants.*;
import static com.android.resources.ScreenOrientation.PORTRAIT;
import static com.android.tools.idea.common.analytics.UsageTrackerUtil.CUSTOM_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlUsageTrackerManagerTest extends AndroidTestCase {
  private static final Executor SYNC_EXECUTOR = Runnable::run;
  private static final String ATTR_CUSTOM_NAME = "MyCustomPropertyName";

  private NlModel myModel;
  private AttributeDefinition myCollapseParallaxMultiplierDefinition;
  private AttributeDefinition myElevationDefinition;
  private AttributeDefinition myTextDefinition;
  private AttributeDefinition myCustomDefinition;
  private final VirtualTimeScheduler myVirtualTimeScheduler = new VirtualTimeScheduler();
  private TestUsageTracker usageTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AnalyticsSettingsData settings = new AnalyticsSettingsData();
    AnalyticsSettings.setInstanceForTest(settings);
    usageTracker = new TestUsageTracker(myVirtualTimeScheduler);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      usageTracker.close();
    } finally {
      super.tearDown();
    }
  }

  @NotNull
  private AndroidStudioEvent getLastLogUsage() {
    List<LoggedUsage> usages = usageTracker.getUsages();
    assertNotEmpty(usages);
    return usages.get(usages.size() - 1).getStudioEvent();
  }

  public void testGetInstance() {
    // Because we are testing the actual getInstanceInner instantiation, we tell the method
    assertEquals(NlUsageTrackerManager.NOP_TRACKER, NlUsageTrackerManager.getInstanceInner(null, true));

    NlDesignSurface surface1 = mock(NlDesignSurface.class);
    NlDesignSurface surface2 = mock(NlDesignSurface.class);
    NlUsageTracker nlUsageTracker = NlUsageTrackerManager.getInstanceInner(surface1, true);
    assertNotEquals(NlUsageTrackerManager.NOP_TRACKER, surface1);
    assertEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface1, true));
    assertNotEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface2, true));
  }

  // b/110242994
  public void ignore_testBasicLogging() {
    NlUsageTracker tracker = getUsageTracker();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    assertEquals(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR, studioEvent.getCategory());
    assertEquals(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT, studioEvent.getKind());
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE,
                 studioEvent.getLayoutEditorEvent().getType());
    // Verify state
    LayoutEditorState state = studioEvent.getLayoutEditorEvent().getState();
    assertEquals(LayoutEditorState.Type.LAYOUT, state.getType());
    assertEquals(LayoutEditorState.Surfaces.BOTH, state.getSurfaces());
    assertEquals(SystemInfoRt.isMac && UIUtil.isRetina() ? 100 : 50, state.getConfigZoomLevel());
    assertEquals("mock", state.getConfigApiLevel());
    assertEquals(LayoutEditorState.Orientation.PORTRAIT, state.getConfigOrientation());
    usageTracker.getUsages().clear();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
    studioEvent = getLastLogUsage();
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL,
                 studioEvent.getLayoutEditorEvent().getType());

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
    studioEvent = getLastLogUsage();
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL,
                 studioEvent.getLayoutEditorEvent().getType());
  }

  public void testRenderLogging() {
    NlUsageTracker tracker = getUsageTracker();

    Result renderResult = mock (Result.class);
    when(renderResult.getStatus()).thenReturn(Result.Status.SUCCESS);
    HtmlLinkManager linkManager = mock(HtmlLinkManager.class);
    RenderLogger logger = mock(RenderLogger.class);
    when(logger.getLinkManager()).thenReturn(linkManager);
    ImmutableMap<String, Throwable> brokenClasses = ImmutableMap.of("com.test.mock", new Throwable("mock error"));
    when(logger.getBrokenClasses()).thenReturn(brokenClasses);
    RenderResult result = mock(RenderResult.class);
    ViewInfo rootView = new ViewInfo("ConstraintLayout", null, 0, 0, 50, 50);
    rootView.setChildren(ImmutableList.of(new ViewInfo("TextView", null, 0, 0, 30, 20)));
    when(result.getRootViews()).thenReturn(ImmutableList.of(rootView));
    when(result.getRenderResult()).thenReturn(renderResult);
    when(result.getLogger()).thenReturn(logger);
    when(result.getModule()).thenReturn(new MockModule(getProject(), getTestRootDisposable()));

    tracker.logRenderResult(LayoutEditorRenderResult.Trigger.EDIT, result, 230);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutEditorRenderResult loggedResult = studioEvent.getLayoutEditorEvent().getRenderResult();
    assertEquals(Result.Status.SUCCESS.ordinal(), loggedResult.getResultCode());
    assertEquals(230, loggedResult.getTotalRenderTimeMs());
    assertEquals(2, loggedResult.getComponentCount());
    assertEquals(1, loggedResult.getTotalIssueCount());
    assertEquals(1, loggedResult.getErrorCount());
    assertEquals(0, loggedResult.getFidelityWarningCount());
  }

  public void testPaletteDropLogging() {
    NlUsageTracker tracker = getUsageTracker();

    tracker.logDropFromPalette(CONSTRAINT_LAYOUT.defaultName(), "<" + CONSTRAINT_LAYOUT.defaultName() + "/>", "All", -1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo("ConstraintLayout");
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
  }

  public void testPaletteDropTextEditLogging() {
    NlUsageTracker tracker = getUsageTracker();

    @Language("XML")
    String representation = "            <EditText\n" +
                            "              android:layout_width=\"wrap_content\"\n" +
                            "              android:layout_height=\"wrap_content\"\n" +
                            "              android:inputType=\"textEmailAddress\"\n" +
                            "              android:ems=\"10\"\n" +
                            "            />\n";

    tracker.logDropFromPalette(EDIT_TEXT, representation, "All", -1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(EDIT_TEXT);
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.EMAIL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
  }

  public void testPaletteDropCustomViewLogging() {
    String tag = "com.acme.MyCustomControl";
    NlUsageTracker tracker = getUsageTracker();

    tracker.logDropFromPalette(tag, "<" + tag + "/>", "Advanced", 1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ADVANCED);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.SINGLE_MATCH);
  }

  public void testPropertyChangeLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = Collections.singletonList(getComponentMock(BUTTON));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_ELEVATION);
    when(property.getNamespace()).thenReturn(ANDROID_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myElevationDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.INSPECTOR, -1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(ATTR_ELEVATION);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.INSPECTOR);
    assertThat(logged.getViewCount()).isEqualTo(1);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
  }

  public void testToolsPropertyChangeLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = Collections.singletonList(getComponentMock(BUTTON));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_ELEVATION);
    when(property.getNamespace()).thenReturn(TOOLS_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myElevationDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 3);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(ATTR_ELEVATION);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.MULTIPLE_MATCHES);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
    assertThat(logged.getViewCount()).isEqualTo(1);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
  }

  public void testSupportPropertyChangeLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = Collections.singletonList(getComponentMock(BUTTON));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    when(property.getNamespace()).thenReturn(AUTO_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myCollapseParallaxMultiplierDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 3);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.MULTIPLE_MATCHES);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
    assertThat(logged.getViewCount()).isEqualTo(1);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
  }

  public void testToolsSupportPropertyChangeLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = Collections.singletonList(getComponentMock(BUTTON));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    when(property.getNamespace()).thenReturn(TOOLS_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myCollapseParallaxMultiplierDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 3);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.MULTIPLE_MATCHES);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
    assertThat(logged.getViewCount()).isEqualTo(1);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
  }

  public void testCustomPropertyChangeLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = Collections.singletonList(getComponentMock(BUTTON));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_CUSTOM_NAME);
    when(property.getNamespace()).thenReturn(AUTO_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myCustomDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.SINGLE_MATCH);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
    assertThat(logged.getViewCount()).isEqualTo(1);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
  }

  public void testPropertyChangeMultipleViewsLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();
    List<NlComponent> components = ImmutableList.of(
      getComponentMock(BUTTON),
      getComponentMock(FLOATING_ACTION_BUTTON.defaultName()),
      getComponentMock("com.acme.MyCustomView"));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_TEXT);
    when(property.getNamespace()).thenReturn(ANDROID_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myTextDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getAttributeChangeEvent();
    assertThat(logged.getAttribute().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getAttribute().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.SINGLE_MATCH);
    assertThat(logged.getViewType()).isEqualTo(LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE);
    assertThat(logged.getViewCount()).isEqualTo(3);
    assertThat(logged.getView(0).getTagName()).isEqualTo(BUTTON);
    assertThat(logged.getView(1).getTagName()).isEqualTo("FloatingActionButton");
    assertThat(logged.getView(2).getTagName()).isEqualTo(CUSTOM_NAME);
  }

  public void testAddFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange(ATTR_TEXT, "", ImmutableList.of(ATTR_COLLAPSE_PARALLAX_MULTIPLIER, ATTR_TEXT), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getAdded().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getAdded().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.hasRemoved()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(2);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    assertThat(logged.getActive(1).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getActive(1).getAttributeName()).isEqualTo(ATTR_TEXT);
  }

  public void testAddCustomFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange(TOOLS_NS_NAME_PREFIX + ATTR_CUSTOM_NAME, "",
                               ImmutableList.of(ATTR_CUSTOM_NAME, TOOLS_NS_NAME_PREFIX + ATTR_COLLAPSE_PARALLAX_MULTIPLIER), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getAdded().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getAdded().getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.hasRemoved()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(2);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getActive(1).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getActive(1).getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
  }

  public void testRemoveFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange("", TOOLS_NS_NAME_PREFIX + ATTR_TEXT, ImmutableList.of(ATTR_ELEVATION), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getRemoved().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getRemoved().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.hasAdded()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(1);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(ATTR_ELEVATION);
  }

  private NlUsageTracker getUsageTracker() {
    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(surface.getSceneMode()).thenReturn(SceneMode.BOTH);
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfiguration()).thenReturn(configuration);

    return new NlUsageTrackerManager(SYNC_EXECUTOR, surface, usageTracker::logNow) {
      @Override
      boolean shouldLog(int percent) {
        // Log everything in tests
        return true;
      }
    };
  }

  private static Configuration getConfigurationMock() {
    IAndroidTarget target = mock(IAndroidTarget.class);
    when(target.getVersion()).thenReturn(new AndroidVersion(0, "mock"));

    State state = mock(State.class);
    when(state.getOrientation()).thenReturn(PORTRAIT);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getTarget()).thenReturn(target);
    when(configuration.getDeviceState()).thenReturn(state);

    return configuration;
  }

  private void initNeleModelMocks() {
    ModuleResourceManagers moduleResourceManagers = mock(ModuleResourceManagers.class);
    FrameworkResourceManager frameworkResourceManager = mock(FrameworkResourceManager.class);
    LocalResourceManager localResourceManager = mock(LocalResourceManager.class);
    AttributeDefinitions systemAttributeDefinitions = mock(AttributeDefinitions.class);
    AttributeDefinitions localAttributeDefinitions = mock(AttributeDefinitions.class);

    myElevationDefinition = new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_ELEVATION);
    myTextDefinition = new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_TEXT);
    myCustomDefinition =
        new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_CUSTOM_NAME, "com.acme:CustomLibrary", null);
    myCollapseParallaxMultiplierDefinition =
        new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER, DESIGN_LIB_ARTIFACT, null);

    myModel = mock(NlModel.class);
    when(myModel.getFacet()).thenReturn(myFacet);

    UsageTrackerUtilTest.registerComponentInstance((MutablePicoContainer)myModule.getPicoContainer(),
                                                   ModuleResourceManagers.class,
                                                   moduleResourceManagers,
                                                   getTestRootDisposable());

    when(moduleResourceManagers.getLocalResourceManager()).thenReturn(localResourceManager);
    when(moduleResourceManagers.getFrameworkResourceManager()).thenReturn(frameworkResourceManager);
    when(localResourceManager.getAttributeDefinitions()).thenReturn(localAttributeDefinitions);
    when(frameworkResourceManager.getAttributeDefinitions()).thenReturn(systemAttributeDefinitions);
    when(localAttributeDefinitions.getAttrs())
        .thenReturn(ImmutableSet.of(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER)));
    when(localAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER)))
        .thenReturn(myCollapseParallaxMultiplierDefinition);
    when(localAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_CUSTOM_NAME)))
        .thenReturn(myCustomDefinition);
    when(systemAttributeDefinitions.getAttrs())
        .thenReturn(ImmutableSet.of(
            ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ELEVATION),
            ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TEXT)));
    when(systemAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ELEVATION)))
        .thenReturn(myElevationDefinition);
    when(systemAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TEXT)))
        .thenReturn(myTextDefinition);
  }

  private NlComponent getComponentMock(@NotNull String tagName) {
    NlComponent component = mock(NlComponent.class);
    when(component.getModel()).thenReturn(myModel);
    when(component.getTagName()).thenReturn(tagName);
    return component;
  }
}
