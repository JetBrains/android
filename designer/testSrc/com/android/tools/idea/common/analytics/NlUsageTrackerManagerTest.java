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

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.mock.MockModule;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import static com.android.SdkConstants.*;
import static com.android.resources.ScreenOrientation.PORTRAIT;
import static com.android.tools.idea.common.analytics.UsageTrackerUtil.CUSTOM_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class NlUsageTrackerManagerTest extends JavaCodeInsightFixtureTestCase {
  private static final Executor SYNC_EXECUTOR = Runnable::run;
  private static final String ATTR_CUSTOM_NAME = "MyCustomPropertyName";

  private LinkedList<AndroidStudioEvent> myLogCalls;
  private NlModel myModel;
  private AndroidFacet myFacet;
  private AttributeDefinition myCollapseParallaxMultiplierDefinition;
  private AttributeDefinition myElevationDefinition;
  private AttributeDefinition myTextDefinition;
  private AttributeDefinition myCustomDefinition;

  public void testGetInstance() {
    assertEquals(NlUsageTrackerManager.NOP_TRACKER, NlUsageTrackerManager.getInstanceInner(null));

    NlDesignSurface surface1 = mock(NlDesignSurface.class);
    NlDesignSurface surface2 = mock(NlDesignSurface.class);
    NlUsageTracker nlUsageTracker = NlUsageTrackerManager.getInstanceInner(surface1);
    assertNotEquals(NlUsageTrackerManager.NOP_TRACKER, surface1);
    assertEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface1));
    assertNotEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface2));
  }

  public void testBasicLogging() {
    NlUsageTracker tracker = getUsageTracker();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
    assertEquals(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR, studioEvent.getCategory());
    assertEquals(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT, studioEvent.getKind());
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE,
                 studioEvent.getLayoutEditorEvent().getType());
    // Verify state
    LayoutEditorState state = studioEvent.getLayoutEditorEvent().getState();
    assertEquals(LayoutEditorState.Type.LAYOUT, state.getType());
    assertEquals(LayoutEditorState.Surfaces.BOTH, state.getSurfaces());
    assertEquals(SystemInfo.isMac && UIUtil.isRetina() ? 100 : 50, state.getConfigZoomLevel());
    assertEquals("mock", state.getConfigApiLevel());
    assertEquals(LayoutEditorState.Orientation.PORTRAIT, state.getConfigOrientation());
    myLogCalls.clear();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
    assertEquals(1, myLogCalls.size());
    studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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

    tracker.logDropFromPalette(CONSTRAINT_LAYOUT, "<" + CONSTRAINT_LAYOUT + "/>", PaletteMode.ICON_AND_NAME, "All", -1);
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo("ConstraintLayout");
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
    assertThat(logged.getViewType()).isEqualTo(LayoutPaletteEvent.ViewType.ICON_AND_NAME);
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

    tracker.logDropFromPalette(EDIT_TEXT, representation, PaletteMode.ICON_AND_NAME, "All", -1);
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(EDIT_TEXT);
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.EMAIL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
    assertThat(logged.getViewType()).isEqualTo(LayoutPaletteEvent.ViewType.ICON_AND_NAME);
  }

  public void testPaletteDropCustomViewLogging() {
    String tag = "com.acme.MyCustomControl";
    NlUsageTracker tracker = getUsageTracker();

    tracker.logDropFromPalette(tag, "<" + tag + "/>", PaletteMode.LARGE_ICONS, "Advanced", 1);
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getViewOption()).isEqualTo(LayoutPaletteEvent.ViewOption.NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ADVANCED);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.SINGLE_MATCH);
    assertThat(logged.getViewType()).isEqualTo(LayoutPaletteEvent.ViewType.LARGE_IONS);
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
      getComponentMock(FLOATING_ACTION_BUTTON),
      getComponentMock("com.acme.MyCustomView"));
    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(ATTR_TEXT);
    when(property.getNamespace()).thenReturn(ANDROID_URI);
    when(property.getComponents()).thenReturn(components);
    when(property.getModel()).thenReturn(myModel);
    when(property.getDefinition()).thenReturn(myTextDefinition);

    tracker.logPropertyChange(property, PropertiesViewMode.TABLE, 1);
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
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
    assertEquals(1, myLogCalls.size());
    AndroidStudioEvent studioEvent = myLogCalls.getFirst();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getRemoved().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getRemoved().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.hasAdded()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(1);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(ATTR_ELEVATION);
  }

  private NlUsageTrackerManager getUsageTracker() {
    UsageTracker usageTracker = mock(UsageTracker.class);
    myLogCalls = new LinkedList<>();
    doAnswer(invocation -> {
      myLogCalls.add(((AndroidStudioEvent.Builder)invocation.getArguments()[0]).build());
      return null;
    }).when(usageTracker).log(any());

    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(surface.getScreenMode()).thenReturn(NlDesignSurface.ScreenMode.BOTH);
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfiguration()).thenReturn(configuration);

    return new NlUsageTrackerManager(SYNC_EXECUTOR, surface, usageTracker) {
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
    myModel = mock(NlModel.class);
    myFacet = mock(AndroidFacet.class);
    ModuleResourceManagers moduleResourceManagers = mock(ModuleResourceManagers.class);
    SystemResourceManager systemResourceManager = mock(SystemResourceManager.class);
    LocalResourceManager localResourceManager = mock(LocalResourceManager.class);
    AttributeDefinitions systemAttributeDefinitions = mock(AttributeDefinitions.class);
    AttributeDefinitions localAttributeDefinitions = mock(AttributeDefinitions.class);

    myElevationDefinition = new AttributeDefinition(ATTR_ELEVATION, null, null, Collections.emptySet());
    myTextDefinition = new AttributeDefinition(ATTR_TEXT, null, null, Collections.emptySet());
    myCustomDefinition = new AttributeDefinition(ATTR_CUSTOM_NAME, "com.acme:CustomLibrary", null, Collections.emptySet());
    myCollapseParallaxMultiplierDefinition =
      new AttributeDefinition(ATTR_COLLAPSE_PARALLAX_MULTIPLIER, DESIGN_LIB_ARTIFACT, null, Collections.emptySet());

    when(myModel.getFacet()).thenReturn(myFacet);
    when(myFacet.getUserData(ModuleResourceManagers.KEY)).thenReturn(moduleResourceManagers);
    when(moduleResourceManagers.getLocalResourceManager()).thenReturn(localResourceManager);
    when(moduleResourceManagers.getSystemResourceManager()).thenReturn(systemResourceManager);
    when(localResourceManager.getAttributeDefinitions()).thenReturn(localAttributeDefinitions);
    when(systemResourceManager.getAttributeDefinitions()).thenReturn(systemAttributeDefinitions);
    when(localAttributeDefinitions.getAttributeNames()).thenReturn(ImmutableSet.of(ATTR_COLLAPSE_PARALLAX_MULTIPLIER));
    when(localAttributeDefinitions.getAttrDefByName(ATTR_COLLAPSE_PARALLAX_MULTIPLIER)).thenReturn(myCollapseParallaxMultiplierDefinition);
    when(localAttributeDefinitions.getAttrDefByName(ATTR_CUSTOM_NAME)).thenReturn(myCustomDefinition);
    when(systemAttributeDefinitions.getAttributeNames()).thenReturn(ImmutableSet.of(ATTR_TEXT, ATTR_ELEVATION));
    when(systemAttributeDefinitions.getAttrDefByName(ATTR_ELEVATION)).thenReturn(myElevationDefinition);
    when(systemAttributeDefinitions.getAttrDefByName(ATTR_TEXT)).thenReturn(myTextDefinition);
  }

  private NlComponent getComponentMock(@NotNull String tagName) {
    NlComponent component = mock(NlComponent.class);
    when(component.getModel()).thenReturn(myModel);
    when(component.getTagName()).thenReturn(tagName);
    return component;
  }
}
