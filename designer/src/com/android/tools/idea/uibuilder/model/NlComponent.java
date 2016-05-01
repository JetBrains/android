/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Represents a component editable in the UI builder. A component has properties,
 * if visual it has bounds, etc.
 */
public class NlComponent {
  private static final Collection<String> TAGS_THAT_DONT_NEED_DEFAULT_IDS = ImmutableSet.of(
    PreferenceTags.CHECK_BOX_PREFERENCE,
    PreferenceTags.EDIT_TEXT_PREFERENCE,
    PreferenceTags.LIST_PREFERENCE,
    PreferenceTags.MULTI_SELECT_LIST_PREFERENCE,
    PreferenceTags.PREFERENCE_CATEGORY,
    PreferenceTags.RINGTONE_PREFERENCE,
    PreferenceTags.SWITCH_PREFERENCE,
    REQUEST_FOCUS,
    SPACE,
    VIEW_INCLUDE,
    VIEW_MERGE
  );

  @Nullable public List<NlComponent> children;
  @Nullable public ViewInfo viewInfo;
  @AndroidCoordinate public int x;
  @AndroidCoordinate public int y;
  @AndroidCoordinate public int w;
  @AndroidCoordinate public int h;
  private NlComponent myParent;
  @NotNull private final NlModel myModel;
  @NotNull private XmlTag myTag;
  @NotNull private String myTagName; // for non-read lock access elsewhere
  @Nullable private TagSnapshot mySnapshot;

  public NlComponent(@NotNull NlModel model, @NotNull XmlTag tag) {
    this(model, tag, null);
  }

  NlComponent(@NotNull NlModel model, @Nullable TagSnapshot snapshot) {
    this(model, snapshot == null ? EmptyXmlTag.INSTANCE : snapshot.tag == null ? EmptyXmlTag.INSTANCE : snapshot.tag, snapshot);
  }

  private NlComponent(@NotNull NlModel model, @NotNull XmlTag tag, @Nullable TagSnapshot snapshot) {
    myModel = model;
    myTag = tag;
    myTagName = tag.getName();
    mySnapshot = snapshot;
  }

  @NotNull
  public XmlTag getTag() {
    return myTag;
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  public void setTag(@NotNull XmlTag tag) {
    myTag = tag;
    myTagName = tag.getName();
  }

  @Nullable
  public TagSnapshot getSnapshot() {
    return mySnapshot;
  }

  public void setSnapshot(@Nullable TagSnapshot snapshot) {
    mySnapshot = snapshot;
  }

  public void setBounds(@AndroidCoordinate int x, @AndroidCoordinate int y, @AndroidCoordinate int w, @AndroidCoordinate int h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
  }

  public void addChild(@NotNull NlComponent component) {
    addChild(component, null);
  }

  public void addChild(@NotNull NlComponent component, @Nullable NlComponent before) {
    if (children == null) {
      children = Lists.newArrayList();
    }
    int index = before != null ? children.indexOf(before) : -1;
    if (index != -1) {
      children.add(index, component);
    }
    else {
      children.add(component);
    }
    component.setParent(this);
  }

  public void removeChild(@NotNull NlComponent component) {
    if (children != null) {
      children.remove(component);
    }
    component.setParent(null);
  }

  public void setChildren(@Nullable List<NlComponent> components) {
    children = components;
    if (components != null) {
      for (NlComponent component : components) {
        component.setParent(this);
      }
    }
  }

  @NotNull
  public Iterable<NlComponent> getChildren() {
    return children != null ? children : Collections.emptyList();
  }

  public int getChildCount() {
    return children != null ? children.size() : 0;
  }

  @Nullable
  public NlComponent getChild(int index) {
    return children != null && index >= 0 && index < children.size() ? children.get(index) : null;
  }

  @Nullable
  public NlComponent getNextSibling() {
    if (myParent == null) {
      return null;
    }
    for (int index = 0; index < myParent.getChildCount(); index++) {
      if (myParent.getChild(index) == this) {
        return myParent.getChild(index + 1);
      }
    }
    return null;
  }

  @Nullable
  public NlComponent findViewByTag(@NotNull XmlTag tag) {
    if (myTag == tag) {
      return this;
    }

    if (children != null) {
      for (NlComponent child : children) {
        NlComponent result = child.findViewByTag(tag);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nullable
  public List<NlComponent> findViewsByTag(@NotNull XmlTag tag) {
    List<NlComponent> result = null;

    if (children != null) {
      for (NlComponent child : children) {
        List<NlComponent> matches = child.findViewsByTag(tag);
        if (matches != null) {
          if (result != null) {
            result.addAll(matches);
          }
          else {
            result = matches;
          }
        }
      }
    }

    if (myTag == tag) {
      if (result == null) {
        return Lists.newArrayList(this);
      }
      result.add(this);
    }

    return result;
  }


  @Nullable
  public NlComponent findLeafAt(@AndroidCoordinate int px, @AndroidCoordinate int py) {
    if (children != null) {
      // Search BACKWARDS such that if the children are painted on top of each
      // other (as is the case in a FrameLayout) I pick the last one which will
      // be topmost!
      for (int i = children.size() - 1; i >= 0; i--) {
        NlComponent child = children.get(i);
        NlComponent result = child.findLeafAt(px, py);
        if (result != null) {
          return result;
        }
      }
    }

    return (x <= px && y <= py && x + w >= px && y + h >= py) ? this : null;
  }

  public boolean isRoot() {
    return !(myTag.getParent() instanceof XmlTag);
  }

  public NlComponent getRoot() {
    NlComponent component = this;
    while (component != null && !component.isRoot()) {
      component = component.getParent();
    }
    return component;
  }

  /**
   * Returns the ID of this component
   */
  @Nullable
  public String getId() {
    String id = getAttribute(ANDROID_URI, ATTR_ID);
    if (id != null) {
      if (id.startsWith(NEW_ID_PREFIX)) {
        return id.substring(NEW_ID_PREFIX.length());
      }
      else if (id.startsWith(ID_PREFIX)) {
        return id.substring(ID_PREFIX.length());
      }
    }
    return null;
  }

  /**
   * Determines whether the given new component should have an id attribute.
   * This is generally false for layouts, and generally true for other views,
   * not including the {@code <include>} and {@code <merge>} tags. Note that
   * {@code <fragment>} tags <b>should</b> specify an id.
   *
   * @return true if the component should have a default id
   */
  public boolean needsDefaultId() {
    if (TAGS_THAT_DONT_NEED_DEFAULT_IDS.contains(myTagName)) {
      return false;
    }

    // Handle <Space> in the compatibility library package
    if (myTagName.endsWith(SPACE) && myTagName.length() > SPACE.length() && myTagName.charAt(myTagName.length() - SPACE.length()) == '.') {
      return false;
    }

    // Assign id's to ViewGroups like ListViews, but not to views like LinearLayout
    ViewHandler viewHandler = getViewHandler();
    if (viewHandler == null) {
      if (myTagName.endsWith("Layout")) {
        return false;
      }
    }
    else if (viewHandler instanceof ViewGroupHandler) {
      return false;
    }

    return true;
  }

  /**
   * Returns the ID, but also assigns a default id if the component does not already have an id (even if the component does
   * not need one according to {@link #needsDefaultId()}
   */
  public String ensureId() {
    String id = getId();
    if (id != null) {
      return id;
    }

    return assignId();
  }

  private String assignId() {
    Collection<String> idList = getIds(myModel.getFacet());
    return assignId(this, idList);
  }

  public static String assignId(@NotNull NlComponent component, @NotNull Collection<String> idList) {
    String idValue = StringUtil.decapitalize(component.getTagName());

    Module module = component.getModel().getModule();
    Project project = module.getProject();
    idValue = ResourceHelper.prependResourcePrefix(module, idValue);

    String nextIdValue = idValue;
    int index = 0;

    // Ensure that we don't create something like "switch" as an id, which won't compile when used
    // in the R class
    NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(JavaLanguage.INSTANCE);

    while (idList.contains(nextIdValue) || validator != null && validator.isKeyword(nextIdValue, project)) {
      ++index;
      if (index == 1 && (validator == null || !validator.isKeyword(nextIdValue, project))) {
        nextIdValue = idValue;
      }
      else {
        nextIdValue = idValue + Integer.toString(index);
      }
    }

    String newId = idValue + (index == 0 ? "" : Integer.toString(index));
    component.setAttribute(ANDROID_URI, ATTR_ID, NEW_ID_PREFIX + newId);
    return newId;
  }

  /**
   * Looks up the existing set of id's reachable from the given module
   */
  public static Collection<String> getIds(@NotNull AndroidFacet facet) {
    AppResourceRepository resources = AppResourceRepository.getAppResources(facet, true);
    return resources.getItemsOfType(ResourceType.ID);
  }

  public int getBaseline() {
    try {
      if (viewInfo != null) {
        Object viewObject = viewInfo.getViewObject();
        return (Integer)viewObject.getClass().getMethod("getBaseline").invoke(viewObject);
      }
    }
    catch (Throwable ignore) {
    }

    return -1;
  }

  private Insets myMargins;
  private Insets myPadding;

  private static int fixDefault(int value) {
    return value == Integer.MIN_VALUE ? 0 : value;
  }

  @NotNull
  public Insets getMargins() {
    if (myMargins == null) {
      if (viewInfo == null) {
        return Insets.NONE;
      }
      try {
        Object layoutParams = viewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams));
        int top = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams));
        int right = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams));
        int bottom = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams));
        // Doesn't look like we need to read startMargin and endMargin here;
        // ViewGroup.MarginLayoutParams#doResolveMargins resolves and assigns values to the others

        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myMargins = Insets.NONE;
        }
        else {
          myMargins = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myMargins = Insets.NONE;
      }
    }
    return myMargins;
  }

  @NotNull
  public Insets getPadding() {
    if (myPadding == null) {
      if (viewInfo == null) {
        return Insets.NONE;
      }
      try {
        Object layoutParams = viewInfo.getViewObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault((Integer)layoutClass.getMethod("getPaddingLeft").invoke(layoutParams)); // TODO: getPaddingStart!
        int top = fixDefault((Integer)layoutClass.getMethod("getPaddingTop").invoke(layoutParams));
        int right = fixDefault((Integer)layoutClass.getMethod("getPaddingRight").invoke(layoutParams));
        int bottom = fixDefault((Integer)layoutClass.getMethod("getPaddingBottom").invoke(layoutParams));
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myPadding = Insets.NONE;
        }
        else {
          myPadding = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myPadding = Insets.NONE;
      }
    }
    return myPadding;
  }

  @Nullable
  public NlComponent getParent() {
    return myParent;
  }

  private void setParent(@Nullable NlComponent parent) {
    myParent = parent;
  }

  @NotNull
  public String getTagName() {
    return myTagName;
  }

  @Override
  public String toString() {
    ToStringHelper helper = Objects.toStringHelper(this).omitNullValues()
      .add("tag", "<" + myTag.getName() + ">")
      .add("bounds", "[" + x + "," + y + ":" + w + "x" + h);
    return helper.toString();
  }

  public void setAndroidAttribute(@NotNull String name, @Nullable String value) {
    setAttribute(ANDROID_URI, name, value);
  }

  /**
   * Convenience wrapper for now; this should be replaced with property lookup
   */
  public void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {
    String prefix = null;
    if (namespace != null) {
      prefix = AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), namespace, null);
    }

    // Handle validity
    myTag.setAttribute(attribute, namespace, value);
    if (mySnapshot != null) {
      mySnapshot.setAttribute(attribute, namespace, prefix, value);
    }
  }

  public void removeAndroidAttribute(@NotNull String name) {
    setAttribute(ANDROID_URI, name, null);
  }

  @Nullable
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (mySnapshot != null) {
      return mySnapshot.getAttribute(attribute, namespace);
    }
    else if (myTag.isValid()) {
      /* TODO: Grab readlock around isValid too (maybe move into getAttributeSafely)
com.intellij.openapi.application.impl.ApplicationImpl$NoReadAccessException
	at com.intellij.openapi.application.impl.ApplicationImpl.assertReadAccessAllowed(ApplicationImpl.java:1056)
	at com.intellij.psi.impl.source.tree.SharedImplUtil.findFileElement(SharedImplUtil.java:88)
	at com.intellij.psi.impl.source.tree.SharedImplUtil.getContainingFile(SharedImplUtil.java:69)
	at com.intellij.psi.impl.source.tree.SharedImplUtil.isValid(SharedImplUtil.java:77)
	at com.intellij.psi.impl.source.tree.CompositePsiElement.isValid(CompositePsiElement.java:130)
	at com.android.tools.idea.uibuilder.model.NlComponent.getAttribute(NlComponent.java:582)
       */
      return AndroidPsiUtils.getAttributeSafely(myTag, namespace, attribute);
    }
    else {
      // Newly created components for example
      return null;
    }
  }

  @NotNull
  public List<AttributeSnapshot> getAttributes() {
    if (mySnapshot != null) {
      return mySnapshot.attributes;
    }

    if (myTag.isValid()) {
      Application application = ApplicationManager.getApplication();

      if (!application.isReadAccessAllowed()) {
        return application.runReadAction((Computable<List<AttributeSnapshot>>)() -> AttributeSnapshot.createAttributesForTag(myTag));
      }
      return AttributeSnapshot.createAttributesForTag(myTag);
    }

    return Collections.emptyList();
  }

  public String ensureNamespace(@NotNull String prefix, @NotNull String namespace) {
    return AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), namespace, prefix);
  }

  public boolean isShowing() {
    return mySnapshot != null;
  }

  @Nullable
  public ViewHandler getViewHandler() {
    if (!myTag.isValid()) {
      return null;
    }
    return ViewHandlerManager.get(myTag.getProject()).getHandler(this);
  }

  @Nullable
  public ViewGroupHandler getViewGroupHandler() {
    if (!myTag.isValid()) {
      return null;
    }
    return ViewHandlerManager.get(myTag.getProject()).findLayoutHandler(this, false);
  }

  /**
   * Creates a new child of the given type, and inserts it before the given sibling (or null to append at the end).
   * Note: This operation can only be called when the caller is already holding a write lock. This will be the
   * case from {@link ViewHandler} callbacks such as {@link ViewHandler#onCreate(ViewEditor, NlComponent, NlComponent, InsertType)}
   * and {@link com.android.tools.idea.uibuilder.api.DragHandler#commit(int, int, int)}.
   *
   * @param editor     The editor showing the component
   * @param fqcn       The fully qualified name of the widget to insert, such as {@code android.widget.LinearLayout}
   *                   You can also pass XML tags here (this is typically the same as the fully qualified class name
   *                   of the custom view, but for Android framework views in the android.view or android.widget packages,
   *                   you can omit the package.)
   * @param before     The sibling to insert immediately before, or null to append
   * @param insertType The type of insertion
   */
  public NlComponent createChild(@NotNull ViewEditor editor,
                                 @NotNull String fqcn,
                                 @Nullable NlComponent before,
                                 @NotNull InsertType insertType) {
    return myModel.createComponent(((ViewEditorImpl)editor).getScreenView(), fqcn, this, before, insertType);
  }

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag
   *
   * @param fqcn the fully qualified class name, such as android.widget.Button
   * @return true if the full package path should be included in the layout XML element
   * tag
   */
  private static boolean viewNeedsPackage(String fqcn) {
    return !(fqcn.startsWith(ANDROID_WIDGET_PREFIX)
             || fqcn.startsWith(ANDROID_VIEW_PKG)
             || fqcn.startsWith(ANDROID_WEBKIT_PKG));
  }

  /**
   * Maps a custom view class to the corresponding layout tag;
   * e.g. {@code android.widget.LinearLayout} maps to just {@code LinearLayout}, but
   * {@code android.support.v4.widget.DrawerLayout} maps to
   * {@code android.support.v4.widget.DrawerLayout}.
   *
   * @param fqcn fully qualified class name
   * @return the corresponding view tag
   */
  @NotNull
  public static String viewClassToTag(@NotNull String fqcn) {
    if (!viewNeedsPackage(fqcn)) {
      return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    return fqcn;
  }

  /**
   * Utility function to extract the id
   *
   * @param str the string to extract the id from
   * @return the string id
   */
  @Nullable
  public static String extractId(@Nullable String str) {
    if (str == null) {
      return null;
    }
    int index = str.lastIndexOf("@id/");
    if (index != -1) {
      return str.substring(index + 4);
    }

    index = str.lastIndexOf("@+id/");

    if (index != -1) {
      return str.substring(index + 5);
    }
    return null;
  }


  /**
   * Returns the value if a String contains a dp value
   */
  public static int extractDp(@Nullable String str) {
    if (str == null) {
      return 0;
    }
    int index = str.lastIndexOf("dp");
    if (index != -1) {
      return Integer.parseInt(str.substring(0, index));
    }
    return 0;
  }
}
