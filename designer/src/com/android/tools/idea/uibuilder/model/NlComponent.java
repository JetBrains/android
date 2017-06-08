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

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.*;
import java.util.stream.Stream;

import static com.android.SdkConstants.*;

/**
 * Represents a component editable in the UI builder. A component has properties,
 * if visual it has bounds, etc.
 */
public class NlComponent implements NlAttributesHolder {

  @Nullable private XmlModelComponentMixin myMixin;

  @Nullable public List<NlComponent> children;
  private NlComponent myParent;
  @NotNull private final NlModel myModel;
  @NotNull private XmlTag myTag;
  @NotNull private String myTagName; // for non-read lock access elsewhere
  @Nullable private TagSnapshot mySnapshot;
  final HashMap<Object, Object> myClientProperties = new HashMap<>();
  private final ArrayList<ChangeListener> myListeners = new ArrayList<>();
  private final ChangeEvent myChangeEvent = new ChangeEvent(this);

  /**
   * Current open attributes transaction or null if none is open
   */
  @Nullable AttributesTransaction myCurrentTransaction;

  public NlComponent(@NotNull NlModel model, @NotNull XmlTag tag) {
    myModel = model;
    myTag = tag;
    myTagName = tag.getName();
  }

  public void setMixin(@NotNull XmlModelComponentMixin mixin) {
    assert myMixin == null;
    myMixin = mixin;
  }

  @Nullable
  public XmlModelComponentMixin getMixin() {
    return myMixin;
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

  public void addChild(@NotNull NlComponent component) {
    addChild(component, null);
  }

  public void addChild(@NotNull NlComponent component, @Nullable NlComponent before) {
    if (component == this) {
      throw new IllegalArgumentException();
    }
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
    if (component == this) {
      throw new IllegalArgumentException();
    }
    if (children != null) {
      children.remove(component);
    }
    component.setParent(null);
  }

  public void setChildren(@Nullable List<NlComponent> components) {
    children = components;
    if (components != null) {
      for (NlComponent component : components) {
        if (component == this) {
          throw new IllegalArgumentException();
        }
        component.setParent(this);
      }
    }
  }

  @NotNull
  public List<NlComponent> getChildren() {
    return children != null ? children : Collections.emptyList();
  }

  public int getChildCount() {
    return children != null ? children.size() : 0;
  }

  @Nullable
  public NlComponent getChild(int index) {
    return children != null && index >= 0 && index < children.size() ? children.get(index) : null;
  }

  @NotNull
  public Stream<NlComponent> flatten() {
    return Stream.concat(
      Stream.of(this),
      getChildren().stream().flatMap(NlComponent::flatten));
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
    String id = myCurrentTransaction != null ? myCurrentTransaction.getAndroidAttribute(ATTR_ID) : getAndroidAttribute(ATTR_ID);

    return stripId(id);
  }

  @Nullable
  public static String stripId(@Nullable String id) {
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
   * Looks up the existing set of id's reachable from the given module
   */
  public static Collection<String> getIds(@NotNull NlModel model) {
    AndroidFacet facet = model.getFacet();
    AppResourceRepository resources = AppResourceRepository.getOrCreateInstance(facet);
    Collection<String> ids = resources.getItemsOfType(ResourceType.ID);
    Set<String> pendingIds = model.getPendingIds();
    if (!pendingIds.isEmpty()) {
      List<String> all = Lists.newArrayListWithCapacity(pendingIds.size() + ids.size());
      all.addAll(ids);
      all.addAll(pendingIds);
      ids = all;
    }
    return ids;
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
    if (this.getMixin() != null) {
      return getMixin().toString();
    }
    return String.format("<%s>", myTagName);
  }

  /**
   * Convenience wrapper for now; this should be replaced with property lookup
   */
  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {
    if (!myTag.isValid()) {
      // This could happen when trying to set an attribute in a component that has been already deleted
      return;
    }

    String prefix = null;
    if (namespace != null && !ANDROID_URI.equals(namespace)) {
      prefix = AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), namespace, null);
    }
    String previous = getAttribute(namespace, attribute);
    if (Objects.equals(previous, value)) {
      return;
    }
    // Handle validity
    myTag.setAttribute(attribute, namespace, value);
    if (mySnapshot != null) {
      mySnapshot.setAttribute(attribute, namespace, prefix, value);
    }
  }

  /**
   * Starts an {@link AttributesTransaction} or returns the current open one.
   */
  @NotNull
  public AttributesTransaction startAttributeTransaction() {
    if (myCurrentTransaction == null) {
      myCurrentTransaction = new AttributesTransaction(this);
    }

    return myCurrentTransaction;
  }

  /**
   * Returns the latest attribute value (either live -- not committed -- or from xml)
   *
   * @param namespace
   * @param attribute
   * @return
   */
  @Nullable
  public String getLiveAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (myCurrentTransaction != null) {
      return myCurrentTransaction.getAttribute(namespace, attribute);
    }
    return getAttribute(namespace, attribute);
  }

  @Override
  @Nullable
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (mySnapshot != null) {
      return mySnapshot.getAttribute(attribute, namespace);
    }
    else if (AndroidPsiUtils.isValid(myTag)) {
      return AndroidPsiUtils.getAttributeSafely(myTag, namespace, attribute);
    }
    else {
      // Newly created components for example
      return null;
    }
  }

  @Nullable
  public String resolveAttribute(@NotNull String namespace, @NotNull String attribute) {
    String attributeValue = getAttribute(namespace, attribute);

    if (attributeValue != null) {
      return attributeValue;
    }

    String styleAttributeValue = getAttribute(null, "style");

    if (styleAttributeValue == null) {
      return null;
    }

    RenderResources resources = myModel.getConfiguration().getResourceResolver();

    if (resources == null) {
      return null;
    }

    // Pretend the style was referenced from a proper resource by constructing a temporary ResourceValue. TODO: aapt namespace?
    ResourceValue tmpResourceValue = new ResourceValue(ResourceUrl.create(null, ResourceType.STYLE, myTagName),
                                                       styleAttributeValue);

    ResourceValue styleResourceValue = resources.resolveResValue(tmpResourceValue);

    if (!(styleResourceValue instanceof StyleResourceValue)) {
      return null;
    }

    ResourceValue itemResourceValue = resources.findItemInStyle((StyleResourceValue)styleResourceValue, attribute, true);

    if (itemResourceValue == null) {
      return null;
    }

    return itemResourceValue.getValue();
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
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @param value
   */
  public final void putClientProperty(Object key, Object value) {
    myClientProperties.put(key, value);
  }

  /**
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @return
   */
  public final Object getClientProperty(Object key) {
    return myClientProperties.get(key);
  }

  /**
   * Removes an element from the cache
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @return
   */
  public final Object removeClientProperty(Object key) {
    return myClientProperties.remove(key);
  }

  /**
   * You can add listeners to track interactive updates
   * Listeners should look at the liveUpdates for changes
   *
   * @param listener
   */
  public void addLiveChangeListener(ChangeListener listener) {
    if (!myListeners.contains(listener)) {
      myListeners.add(listener);
    }
  }

  /**
   * remove a listener you have already added
   *
   * @param listener
   */
  public void removeLiveChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  /**
   * call to notify listeners you have made a "live" change
   */
  public void fireLiveChangeEvent() {
    myListeners.forEach(listener -> listener.stateChanged(myChangeEvent));
  }

  public abstract static class XmlModelComponentMixin {
    private final NlComponent myComponent;

    public XmlModelComponentMixin(@NotNull NlComponent component) {
      myComponent = component;
    }

    @NotNull
    protected NlComponent getComponent() {
      return myComponent;
    }

    @Override
    public String toString() {
      return String.format("<%s>", myComponent.getTagName());
    }
  }
}
