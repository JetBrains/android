/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.legacy.LegacyCallback;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ViewLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Loader for Android Project class in order to use them in the layout editor.
 * <p/>This implements {@link com.android.ide.common.rendering.api.IProjectCallback} for the old and new API through
 * {@link com.android.ide.common.rendering.legacy.LegacyCallback}
 */
public final class ProjectCallback extends LegacyCallback {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ProjectCallback");
  @NotNull private final Module myModule;
  @NotNull private final ProjectResources myProjectRes;
  @NotNull final private LayoutLibrary myLayoutLib;
  @Nullable private String myNamespace;
  @Nullable private RenderLogger myLogger;
  @NotNull private ViewLoader myClassLoader;
  @Nullable private String myLayoutName;
  @Nullable private ILayoutPullParser myLayoutEmbeddedParser;
  @Nullable private ResourceResolver myResourceResolver;
  private boolean myUsed = false;

  /**
   * Creates a new {@link ProjectCallback} to be used with the layout lib.
   *
   * @param layoutLib  The layout library this callback is going to be invoked from
   * @param projectRes the {@link ProjectResources} for the project.
   * @param project    the project.
   */
  public ProjectCallback(@NotNull LayoutLibrary layoutLib, @NotNull ProjectResources projectRes, @NotNull Module project,
                         @NotNull RenderLogger logger) {
    myLayoutLib = layoutLib;
    myProjectRes = projectRes;
    myModule = project;

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    myClassLoader = new ViewLoader(myLayoutLib, facet, myProjectRes, logger);
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems
   *
   * @param logger the new logger to use, or null to clear it out
   */
  public void setLogger(@Nullable RenderLogger logger) {
    myLogger = logger;
    myClassLoader.setLogger(logger);
  }

  /**
   * Returns the {@link com.android.ide.common.rendering.api.LayoutLog} logger used for error messages, or null
   *
   * @return the logger being used, or null if no logger is in use
   */
  @Nullable
  public LayoutLog getLogger() {
    return myLogger;
  }

  /**
   * {@inheritDoc}
   * <p/>
   * This implementation goes through the output directory of the Eclipse project and loads the
   * <code>.class</code> file directly.
   */
  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public Object loadView(@NotNull String className, @NotNull Class[] constructorSignature, @NotNull Object[] constructorParameters)
      throws Exception {
    if (className.indexOf('.') == -1 && !VIEW_FRAGMENT.equals(className) && !VIEW_INCLUDE.equals(className)) {
      // When something is *really* wrong we get asked to load core Android classes.
      // Ignore these; custom views should always have fully qualified names.
      throw new ClassNotFoundException(className);
    }

    myUsed = true;

    return myClassLoader.loadView(className, constructorSignature, constructorParameters);
  }

  /**
   * Returns the namespace for the project. The namespace contains a standard part + the
   * application package.
   *
   * @return The package namespace of the project or null in case of error.
   */
  // TODO: Remove
  @Override
  public String getNamespace() {
    // TODO: use
    //     final String namespace = AndroidXmlSchemaProvider.getLocalXmlNamespace(facet);
    // instead!
    if (myNamespace == null) {
      String javaPackage = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final AndroidFacet facet = AndroidFacet.getInstance(myModule);
          if (facet == null) {
            return null;
          }

          final Manifest manifest = facet.getManifest();
          if (manifest == null) {
            return null;
          }

          return manifest.getPackage().getValue();
        }
      });

      if (javaPackage != null) {
        myNamespace = String.format(NS_CUSTOM_RESOURCES_S, javaPackage);
      }
    }

    return myNamespace;
  }

  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"}) // Required by IProjectCallback
  @Nullable
  @Override
  public com.android.util.Pair<ResourceType, String> resolveResourceId(int id) {
    return myProjectRes.resolveResourceId(id);
  }

  @Nullable
  @Override
  public String resolveResourceId(int[] id) {
    return myProjectRes.resolveStyleable(id);
  }

  @Nullable
  @Override
  public Integer getResourceId(ResourceType type, String name) {
    return myProjectRes.getResourceId(type, name);
  }

  /**
   * Returns whether the loader has received requests to load custom views. Note that
   * the custom view loading may not actually have succeeded; this flag only records
   * whether it was <b>requested</b>.
   * <p/>
   * This allows to efficiently only recreate when needed upon code change in the
   * project.
   *
   * @return true if the loader has been asked to load custom views
   */
  public boolean isUsed() {
    return myUsed;
  }

  public void setLayoutParser(@Nullable String layoutName, @Nullable ILayoutPullParser layoutParser) {
    myLayoutName = layoutName;
    myLayoutEmbeddedParser = layoutParser;
  }

  @SuppressWarnings("deprecation") // Required by IProjectCallback
  @Nullable
  @Override
  public ILayoutPullParser getParser(@NotNull String layoutName) {
    // Try to compute the ResourceValue for this layout since layoutlib
    // must be an older version which doesn't pass the value:
    if (myResourceResolver != null) {
      ResourceValue value = myResourceResolver.getProjectResource(ResourceType.LAYOUT, layoutName);
      if (value != null) {
        return getParser(value);
      }
    }

    return getParser(layoutName, null);
  }

  @Nullable
  @Override
  public ILayoutPullParser getParser(@NotNull ResourceValue layoutResource) {
    return getParser(layoutResource.getName(), new File(layoutResource.getValue()));
  }

  @Nullable
  private ILayoutPullParser getParser(@NotNull String layoutName, @Nullable File xml) {
    if (layoutName.equals(myLayoutName)) {
      ILayoutPullParser parser = myLayoutEmbeddedParser;
      // The parser should only be used once!! If it is included more than once,
      // subsequent includes should just use a plain pull parser that is not tied
      // to the XML model
      myLayoutEmbeddedParser = null;
      return parser;
    }

    // For included layouts, create a ContextPullParser such that we get the
    // layout editor behavior in included layouts as well - which for example
    // replaces <fragment> tags with <include>.
    if (xml != null && xml.isFile()) {
      ContextPullParser parser = new ContextPullParser(this);
      try {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        String xmlText = Files.toString(xml, Charsets.UTF_8);
        parser.setInput(new StringReader(xmlText));
        return parser;
      }
      catch (XmlPullParserException e) {
        LOG.error(e);
      }
      catch (FileNotFoundException e) {
        // Shouldn't happen since we check isFile() above
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  @Nullable
  @Override
  public Object getAdapterItemValue(ResourceReference adapterView,
                                    Object adapterCookie,
                                    ResourceReference itemRef,
                                    int fullPosition,
                                    int typePosition,
                                    int fullChildPosition,
                                    int typeChildPosition,
                                    ResourceReference viewRef,
                                    ViewAttribute viewAttribute,
                                    Object defaultValue) {

    // Special case for the palette preview
    if (viewAttribute == ViewAttribute.TEXT && adapterView.getName().startsWith("android_widget_")) { //$NON-NLS-1$
      String name = adapterView.getName();
      if (viewRef.getName().equals("text2")) { //$NON-NLS-1$
        return "Sub Item";
      }
      if (fullPosition == 0) {
        String viewName = name.substring("android_widget_".length());
        if (viewName.equals(EXPANDABLE_LIST_VIEW)) {
          return "ExpandableList"; // ExpandableListView is too wide, character-wraps
        }
        return viewName;
      }
      else {
        return "Next Item";
      }
    }

    if (itemRef.isFramework()) {
      // Special case for list_view_item_2 and friends
      if (viewRef.getName().equals("text2")) { //$NON-NLS-1$
        return "Sub Item " + (fullPosition + 1);
      }
    }

    if (viewAttribute == ViewAttribute.TEXT && ((String)defaultValue).length() == 0) {
      return "Item " + (fullPosition + 1);
    }

    return null;
  }

  /**
   * For the given class, finds and returns the nearest super class which is a ListView
   * or an ExpandableListView or a GridView (which uses a list adapter), or returns null.
   *
   * @param clz the class of the view object
   * @return the fully qualified class name of the list ancestor, or null if there
   *         is no list view ancestor
   */
  @Nullable
  public static String getListAdapterViewFqcn(@NotNull Class<?> clz) {
    String fqcn = clz.getName();
    if (fqcn.endsWith(LIST_VIEW)) { // including EXPANDABLE_LIST_VIEW
      return fqcn;
    }
    else if (fqcn.equals(FQCN_GRID_VIEW)) {
      return fqcn;
    }
    else if (fqcn.equals(FQCN_SPINNER)) {
      return fqcn;
    }
    else if (fqcn.startsWith(ANDROID_PKG_PREFIX)) {
      return null;
    }
    Class<?> superClass = clz.getSuperclass();
    if (superClass != null) {
      return getListAdapterViewFqcn(superClass);
    }
    else {
      // Should not happen; we would have encountered android.view.View first,
      // and it should have been covered by the ANDROID_PKG_PREFIX case above.
      return null;
    }
  }

  /**
   * Looks at the parent-chain of the view and if it finds a custom view, or a
   * CalendarView, within the given distance then it returns true. A ListView within a
   * CalendarView should not be assigned a custom list view type because it sets its own
   * and then attempts to cast the layout to its own type which would fail if the normal
   * default list item binding is used.
   */
  private boolean isWithinIllegalParent(@NotNull Object viewObject, int depth) {
    String fqcn = viewObject.getClass().getName();
    if (fqcn.endsWith(CALENDAR_VIEW) || !fqcn.startsWith(ANDROID_PKG_PREFIX)) {
      return true;
    }

    if (depth > 0) {
      Result result = myLayoutLib.getViewParent(viewObject);
      if (result.isSuccess()) {
        Object parent = result.getData();
        if (parent != null) {
          return isWithinIllegalParent(parent, depth - 1);
        }
      }
    }

    return false;
  }

  @Nullable
  @Override
  public AdapterBinding getAdapterBinding(final ResourceReference adapterView, final Object adapterCookie, final Object viewObject) {
    // Look for user-recorded preference for layout to be used for previews
    if (adapterCookie instanceof XmlTag) {
      XmlTag uiNode = (XmlTag)adapterCookie;
      AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, uiNode);
      if (binding != null) {
        return binding;
      }
    }
    else if (adapterCookie instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked") Map<String, String> map = (Map<String, String>)adapterCookie;
      AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, map);
      if (binding != null) {
        return binding;
      }
    }

    if (viewObject == null) {
      return null;
    }

    // Is this a ListView or ExpandableListView? If so, return its fully qualified
    // class name, otherwise return null. This is used to filter out other types
    // of AdapterViews (such as Spinners) where we don't want to use the list item
    // binding.
    String listFqcn = getListAdapterViewFqcn(viewObject.getClass());
    if (listFqcn == null) {
      return null;
    }

    // Is this ListView nested within an "illegal" container, such as a CalendarView?
    // If so, don't change the bindings below. Some views, such as CalendarView, and
    // potentially some custom views, might be doing specific things with the ListView
    // that could break if we add our own list binding, so for these leave the list
    // alone.
    if (isWithinIllegalParent(viewObject, 2)) {
      return null;
    }

    int count = listFqcn.endsWith(GRID_VIEW) ? 24 : 12;
    AdapterBinding binding = new AdapterBinding(count);
    if (listFqcn.endsWith(EXPANDABLE_LIST_VIEW)) {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_EXPANDABLE_LIST_ITEM, true /* isFramework */, 1));
    }
    else if (listFqcn.equals(SPINNER)) {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_SPINNER_ITEM, true /* isFramework */, 1));
    }
    else {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_LIST_ITEM, true /* isFramework */, 1));
    }

    return binding;
  }

  /**
   * Sets the {@link ResourceResolver} to be used when looking up resources
   *
   * @param resolver the resolver to use
   */
  public void setResourceResolver(@Nullable ResourceResolver resolver) {
    myResourceResolver = resolver;
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly
   */
  public void loadAndParseRClass() {
    myClassLoader.loadAndParseRClassSilently();
  }
}
