/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.model;


import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Synchronizes a {@linkplain NlModel} such that the component hierarchy
 * is up to date wrt tag snapshots etc. Crucially, it attempts to preserve
 * component hierarchy (since XmlTags may sometimes not survive a PSI reparse, but we
 * want the {@linkplain NlComponent} instances to keep the same instances across these
 * edits such that for example the selection (a set of {@link NlComponent} instances)
 * are preserved.
 */
public class DefaultModelUpdater implements NlModel.NlModelUpdaterInterface {

  private static class ModelUpdaterData {
    protected NlModel myModel;
    protected final Map<XmlTag, NlComponent> myTagToComponentMap = Maps.newIdentityHashMap();
    protected final Map<NlComponent, XmlTag> myComponentToTagMap = Maps.newIdentityHashMap();

    /**
     * Map from snapshots in the old component map to the corresponding components
     */
    protected final Map<TagSnapshot, NlComponent> mySnapshotToComponent = Maps.newIdentityHashMap();

    /**
     * Map from tags in the view render tree to the corresponding snapshots
     */
    protected final Map<XmlTag, TagSnapshot> myTagToSnapshot = Maps.newHashMap();
  }

  private void recordComponentMapping(
      @NotNull XmlTag tag,
      @NotNull NlComponent component,
      ModelUpdaterData data) {
    // Is the component already registered to some other tag?
    XmlTag prevTag = data.myComponentToTagMap.get(component);
    if (prevTag != null) {
      // Yes. Unregister it.
      data.myTagToComponentMap.remove(prevTag);
    }

    data.myComponentToTagMap.put(component, tag);
    data.myTagToComponentMap.put(tag, component);
  }

  /**
   * Update the component hierarchy associated with this {@link NlModel} such
   * that the associated component list correctly reflects the latest versions of the
   * XML PSI file, the given tag snapshot and {@link NlModel.TagSnapshotTreeNode} hierarchy
   */
  @VisibleForTesting
  @Override
  public void update(@NotNull NlModel model, @Nullable XmlTag newRoot, @NotNull List<NlModel.TagSnapshotTreeNode> roots) {
    ModelUpdaterData data = new ModelUpdaterData();

    data.myModel = model;

    if (newRoot == null) {
      data.myModel.setRootComponent(null);
      return;
    }

    // Make sure the root is valid during these operation.
    data.myModel.setRootComponent(ApplicationManager.getApplication().runReadAction((Computable<NlComponent>)() -> {
      if (!newRoot.isValid()) {
        return null;
      }

      // Next find the snapshots corresponding to the missing components.
      // We have to search among the view infos in the new components.
      for (NlModel.TagSnapshotTreeNode root : roots) {
        gatherTagsAndSnapshots(root, data.myTagToSnapshot);
      }

      // Ensure that all XmlTags in the new XmlFile contents map to a corresponding component
      // form the old map
      mapOldToNew(newRoot, data);

      for (Map.Entry<XmlTag, NlComponent> entry : data.myTagToComponentMap.entrySet()) {
        XmlTag tag = entry.getKey();
        NlComponent component = entry.getValue();
        if (!component.getTagName().equals(tag.getName())) {
          // One or more incompatible changes: PSI nodes have been reused unpredictably
          // so completely recompute the hierarchy
          data.myTagToComponentMap.clear();
          data.myComponentToTagMap.clear();
          break;
        }
      }

      // Build up the new component tree
      return createTree(newRoot, data);
    }));

    // Wipe out state in older components to make sure on reuse we don't accidentally inherit old
    // data
    for (NlComponent component : data.myTagToComponentMap.values()) {
      component.setSnapshot(null);
    }

    // Update the components' snapshots
    for (NlModel.TagSnapshotTreeNode root : roots) {
      updateHierarchy(root, data);
    }
  }

  private void mapOldToNew(
      @NotNull XmlTag newRootTag,
      ModelUpdaterData data) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    // First build up a new component tree to reflect the latest XmlFile hierarchy.
    // If there have been no structural changes, these map 1-1 from the previous hierarchy.
    // We first attempt to do it based on the XmlTags:
    //  (1) record a map from XmlTag to NlComponent in the previous component list
    for (NlComponent component : data.myModel.getComponents()) {
      gatherTagsAndSnapshots(component, data);
    }

    // Look for any NlComponents no longer present in the new set
    List<XmlTag> missing = new ArrayList<>();
    Set<XmlTag> remaining = Sets.newIdentityHashSet();
    remaining.addAll(data.myTagToComponentMap.keySet());
    checkMissing(newRootTag, remaining, missing);

    // If we've just removed a component, there will be no missing tags; we
    // can build the new/updated component hierarchy directly from the old
    // NlComponent instances
    if (missing.isEmpty()) {
      return;
    }

    // If we've just added a component, there will be no remaining tags from
    // old component instances. In this case all components should be new
    // instances
    if (remaining.isEmpty()) {
      return;
    }

    // Try to map more component instances from old to new.
    // We will do this via multiple heuristics:
    //   - mapping id's
    //   - looking at all component attributes (e.g. snapshots)

    // First check by id.
    // Note: We can't use XmlTag#getAttribute on the old component hierarchy;
    // those elements may not be valid and PSI will throw exceptions if we
    // attempt to access them.
    Map<String, NlComponent> oldIds = Maps.newHashMap();
    for (Map.Entry<TagSnapshot, NlComponent> entry : data.mySnapshotToComponent.entrySet()) {
      TagSnapshot snapshot = entry.getKey();
      if (snapshot != null) {
        String id = snapshot.getAttribute(ATTR_ID, ANDROID_URI);
        if (id != null) {
          oldIds.put(id, entry.getValue());
        }
      }
    }
    ListIterator<XmlTag> missingIterator = missing.listIterator();
    while (missingIterator.hasNext()) {
      XmlTag tag = missingIterator.next();
      String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
      if (id != null) {
        // TODO: Consider unifying @+id/ and @id/ references here
        // (though it's unlikely for this to change across component
        // synchronization operations)
        NlComponent component = oldIds.get(id);
        if (component != null) {
          recordComponentMapping(tag, component, data);
          remaining.remove(component.getTagDeprecated());
          missingIterator.remove();
        }
      }
    }

    if (missing.isEmpty() || remaining.isEmpty()) {
      // We've now resolved everything
      return;
    }

    // Next attempt to correlate components based on tag snapshots

    // First compute fingerprints of the old components
    Multimap<Long, TagSnapshot> snapshotIds = ArrayListMultimap.create();
    for (XmlTag old : remaining) {
      NlComponent component = data.myTagToComponentMap.get(old);
      if (component != null) { // this *should* be the case
        TagSnapshot snapshot = component.getSnapshot();
        if (snapshot != null) {
          snapshotIds.put(snapshot.getSignature(), snapshot);
        }
      }
    }

    // Note that we're using a multimap rather than a map for these keys,
    // so if you have the same exact element and attributes multiple times,
    // they'll be found and matched in the same order. (This works because
    // we're also tracking the missing xml tags in iteration order by using a
    // list instead of a set.)
    missingIterator = missing.listIterator();
    while (missingIterator.hasNext()) {
      XmlTag tag = missingIterator.next();
      TagSnapshot snapshot = data.myTagToSnapshot.get(tag);
      if (snapshot != null) {
        long signature = snapshot.getSignature();
        Collection<TagSnapshot> snapshots = snapshotIds.get(signature);
        if (!snapshots.isEmpty()) {
          TagSnapshot first = snapshots.iterator().next();
          NlComponent component = data.mySnapshotToComponent.get(first);
          if (component != null) {
            recordComponentMapping(tag, component, data);
            remaining.remove(component.getTagDeprecated());
            missingIterator.remove();
          }
        }
      }
    }

    // Finally, if there's just a single tag in question, it might have been
    // that we changed an attribute of a tag (so the fingerprint no longer matches).
    // If the tag name is identical, we'll go ahead.
    if (missing.size() == 1 && remaining.size() == 1) {
      XmlTag oldTag = remaining.iterator().next();
      NlComponent component = data.myTagToComponentMap.get(oldTag);
      if (component != null) {
        XmlTag newTag = missing.get(0);
        TagSnapshot snapshot = component.getSnapshot();
        if (snapshot != null) {
          if (snapshot.tagName.equals(newTag.getName())) {
            recordComponentMapping(newTag, component, data);
          }
        }
      }
    }
  }

  /**
   * Processes through the XML tag hierarchy recursively, and checks
   * whether the tag is in the remaining set, and if so removes it,
   * otherwise adds it to the missing set.
   */
  private static void checkMissing(XmlTag tag, Set<XmlTag> remaining, List<XmlTag> missing) {

    boolean found = remaining.remove(tag);
    if (!found) {
      missing.add(tag);
    }
    for (XmlTag child : tag.getSubTags()) {
      checkMissing(child, remaining, missing);
    }
  }

  private void gatherTagsAndSnapshots(@NotNull NlComponent component, ModelUpdaterData data) {
    XmlTag tag = component.getTagDeprecated();

    recordComponentMapping(tag, component, data);
    data.mySnapshotToComponent.put(component.getSnapshot(), component);

    for (NlComponent child : component.getChildren()) {
      gatherTagsAndSnapshots(child, data);
    }
  }

  private static void gatherTagsAndSnapshots(@NotNull NlModel.TagSnapshotTreeNode node, @NotNull Map<XmlTag, TagSnapshot> map) {
    TagSnapshot snapshot = node.getTagSnapshot();
    if (snapshot != null) {
      map.put(snapshot.tag, snapshot);
    }

    for (NlModel.TagSnapshotTreeNode child : node.getChildren()) {
      gatherTagsAndSnapshots(child, map);
    }
  }

  @NotNull
  private NlComponent createTree(@NotNull XmlTag tag, ModelUpdaterData data) {
    NlComponent component = data.myTagToComponentMap.get(tag);
    if (component == null) {
      // New component: tag didn't exist in the previous component hierarchy,
      // and no similar tag was found
      component = data.myModel.createComponent(tag);
      recordComponentMapping(tag, component, data);
    }

    XmlTag[] subTags = tag.getSubTags();
    if (subTags.length > 0) {
      if (NlModel.CHECK_MODEL_INTEGRITY) {
        Set<NlComponent> seen = Sets.newHashSet();
        Set<XmlTag> seenTags = Sets.newHashSet();
        for (XmlTag t : subTags) {
          if (seenTags.contains(t)) {
            assert false : t;
          }
          seenTags.add(t);
          NlComponent registeredComponent = data.myTagToComponentMap.get(t);
          if (registeredComponent != null) {
            if (seen.contains(registeredComponent)) {
              assert false : registeredComponent;
            }
            seen.add(registeredComponent);
          }
        }
      }

      List<NlComponent> children = new ArrayList<>(subTags.length);
      for (XmlTag subtag : subTags) {
        NlComponent child = createTree(subtag, data);
        children.add(child);
      }
      component.setChildren(children);
    }
    else {
      component.setChildren(null);
    }

    return component;
  }

  private void updateHierarchy(@NotNull NlModel.TagSnapshotTreeNode node, ModelUpdaterData data) {
    TagSnapshot snapshot = ApplicationManager.getApplication().runReadAction((Computable<? extends TagSnapshot>)node::getTagSnapshot);
    NlComponent component;
    if (snapshot != null) {
      component = data.mySnapshotToComponent.get(snapshot);
      if (component == null) {
        component = data.myTagToComponentMap.get(snapshot.tag);
      }

      if (component != null) {
        component.setSnapshot(snapshot);
        assert snapshot.tag != null;
        component.setTag(snapshot.tag);
      }
    }
    for (NlModel.TagSnapshotTreeNode child : node.getChildren()) {
      updateHierarchy(child, data);
    }
  }
}
