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
package com.android.tools.idea.uibuilder.handlers.relative;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.util.containers.WeakHashMap;

import java.util.*;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VALUE_TRUE;

/**
 * Data structure about relative layout relationships which makes it possible to:
 * <ul>
 * <li> Quickly determine not just the dependencies on other nodes, but which nodes
 * depend on this node such that they can be visualized for the selection
 * <li> Determine if there are cyclic dependencies, and whether a potential move
 * would result in a cycle
 * <li> Determine the "depth" of a given node (in terms of how many connections it
 * is away from a parent edge) such that we can prioritize connections which
 * minimizes the depth
 * </ul>
 */
public class DependencyGraph {
  /**
   * Format to chain constraint dependencies: button 1 above button2 etc
   */
  private static final String DEPENDENCY_FORMAT = "%1$s %2$s %3$s"; //$NON-NLS-1$

  /** Cache for {@link DependencyGraph} */
  private static final WeakHashMap<NlComponent, DependencyGraph> ourCache = new WeakHashMap<NlComponent, DependencyGraph>();

  private final Map<NlComponent, ViewData> myNodeToView = new HashMap<NlComponent, ViewData>();
  private final long myModelVersion;

  /**
   * Returns the {@link DependencyGraph} for the given relative layout widget
   *
   * @param layout the relative layout
   * @return a {@link DependencyGraph} for the layout
   */
  @NotNull
  public static DependencyGraph get(@NotNull NlComponent layout) {
    DependencyGraph dependencyGraph = ourCache.get(layout);
    if (dependencyGraph != null) {
      if (dependencyGraph.myModelVersion == layout.getModel().getModificationCount()) {
        return dependencyGraph;
      }
    }

    dependencyGraph = new DependencyGraph(layout);
    ourCache.put(layout, dependencyGraph);

    return dependencyGraph;
  }

  /**
   * Constructs a new {@link DependencyGraph} for the given relative layout
   */
  private DependencyGraph(NlComponent layout) {
    myModelVersion = layout.getModel().getModificationCount();

    // Parent view:
    String parentId = layout.getId();
    if (parentId != null) {
      parentId = LintUtils.stripIdPrefix(parentId);
    }
    else {
      parentId = "RelativeLayout"; // For display purposes; we never reference
      // the parent id from a constraint, only via parent-relative params
      // like centerInParent
    }
    ViewData parentView = new ViewData(layout, parentId);
    myNodeToView.put(layout, parentView);
    Map<String, ViewData> idToView = new HashMap<String, ViewData>();
    idToView.put(parentId, parentView);

    for (NlComponent child : layout.getChildren()) {
      String id = child.getId();
      if (id != null) {
        id = LintUtils.stripIdPrefix(id);
      }
      ViewData view = new ViewData(child, id);
      myNodeToView.put(child, view);
      if (id != null) {
        idToView.put(id, view);
      }
    }

    for (ViewData view : myNodeToView.values()) {
      for (AttributeSnapshot attribute : view.node.getAttributes()) {
        if (!ANDROID_URI.equals(attribute.namespace)) {
          continue;
        }
        String name = attribute.name;
        ConstraintType type = ConstraintType.fromAttribute(name);
        if (type != null) {
          String value = attribute.value;

          if (type.targetParent) {
            if (VALUE_TRUE.equals(value)) {
              Constraint constraint = new Constraint(type, view, parentView);
              view.dependsOn.add(constraint);
              parentView.dependedOnBy.add(constraint);
            }
          }
          else {
            // id-based constraint.
            // NOTE: The id could refer to some widget that is NOT a sibling!
            String targetId = LintUtils.stripIdPrefix(value);
            ViewData target = idToView.get(targetId);
            //noinspection StatementWithEmptyBody
            if (target != view) {
              if (target != null) {
                Constraint constraint = new Constraint(type, view, target);
                view.dependsOn.add(constraint);
                target.dependedOnBy.add(constraint);
              }
            }
            else {
              // Self-reference. RelativeLayout ignores these so it's
              // not an error like a deeper cycle (where RelativeLayout
              // will throw an exception), but we might as well warn
              // the user about it.
              // TODO: Where do we emit this error?
            }
          }
        }
      }
    }
  }

  public ViewData getView(NlComponent node) {
    return myNodeToView.get(node);
  }

  /**
   * Returns the set of views that depend on the given node in either the horizontal or
   * vertical direction
   *
   * @param nodes    the set of nodes that we want to compute the transitive dependencies
   *                 for
   * @param vertical if true, look for vertical edge dependencies, otherwise look for
   *                 horizontal edge dependencies
   * @return the set of nodes that directly or indirectly depend on the given nodes in
   * the given direction
   */
  public Set<NlComponent> dependsOn(Collection<? extends NlComponent> nodes, boolean vertical) {
    List<ViewData> reachable = new ArrayList<ViewData>();

    // Traverse the graph of constraints and determine all nodes affected by
    // this node
    Set<ViewData> visiting = new HashSet<ViewData>();
    for (NlComponent node : nodes) {
      ViewData view = myNodeToView.get(node);
      if (view != null) {
        findBackwards(view, visiting, reachable, vertical, view);
      }
    }

    Set<NlComponent> dependents = new HashSet<NlComponent>(reachable.size());

    for (ViewData v : reachable) {
      dependents.add(v.node);
    }

    return dependents;
  }

  private void findBackwards(ViewData view, Set<ViewData> visiting, List<ViewData> reachable, boolean vertical, ViewData start) {
    visiting.add(view);
    reachable.add(view);

    for (Constraint constraint : view.dependedOnBy) {
      if (vertical && !constraint.type.verticalEdge || !vertical && !constraint.type.horizontalEdge) {
        continue;
      }

      assert constraint.to == view;
      ViewData from = constraint.from;
      if (visiting.contains(from)) {
        // Cycle - what do we do to highlight this?
        List<Constraint> path = getPathTo(start.node, view.node, vertical);
        if (path != null) {
          // TODO: display to the user somehow. We need log access for the
          // view rules.
          //System.out.println(Constraint.describePath(path, null, null));
        }
      }
      else {
        findBackwards(from, visiting, reachable, vertical, start);
      }
    }

    visiting.remove(view);
  }

  @Nullable
  public List<Constraint> getPathTo(NlComponent from, NlComponent to, boolean vertical) {
    // Traverse the graph of constraints and determine all nodes affected by
    // this node
    Set<ViewData> visiting = new HashSet<ViewData>();
    List<Constraint> path = new ArrayList<Constraint>();
    ViewData view = myNodeToView.get(from);
    if (view != null) {
      return findForwards(view, visiting, path, vertical, to);
    }

    return null;
  }

  @Nullable
  private static List<Constraint> findForwards(ViewData view,
                                               Set<ViewData> visiting,
                                               List<Constraint> path,
                                               boolean vertical,
                                               NlComponent target) {
    visiting.add(view);

    for (Constraint constraint : view.dependsOn) {
      if (vertical && !constraint.type.verticalEdge || !vertical && !constraint.type.horizontalEdge) {
        continue;
      }

      try {
        path.add(constraint);

        if (constraint.to.node == target) {
          return new ArrayList<Constraint>(path);
        }

        assert constraint.from == view;
        ViewData to = constraint.to;
        if (visiting.contains(to)) {
          // CYCLE!
          continue;
        }

        List<Constraint> chain = findForwards(to, visiting, path, vertical, target);
        if (chain != null) {
          return chain;
        }
      }
      finally {
        path.remove(constraint);
      }
    }

    visiting.remove(view);

    return null;
  }

  /**
   * Info about a specific widget child of a relative layout and its constraints. This
   * is a node in the dependency graph.
   */
  static class ViewData {
    @NotNull public final NlComponent node;
    @Nullable public final String id;
    @NotNull public final List<Constraint> dependsOn = new ArrayList<Constraint>(4);
    @NotNull public final List<Constraint> dependedOnBy = new ArrayList<Constraint>(8);

    ViewData(@NotNull NlComponent node, @Nullable String id) {
      this.node = node;
      this.id = id;
    }
  }

  /**
   * Info about a specific constraint between two widgets in a relative layout. This is
   * an edge in the dependency graph.
   */
  static class Constraint {
    @NotNull public final ConstraintType type;
    public final ViewData from;
    public final ViewData to;

    Constraint(@NotNull ConstraintType type, @NotNull ViewData from, @NotNull ViewData to) {
      this.type = type;
      this.from = from;
      this.to = to;
    }

    static String describePath(@NotNull List<Constraint> path, @Nullable String newName, @Nullable String newId) {
      String s = "";
      for (int i = path.size() - 1; i >= 0; i--) {
        Constraint constraint = path.get(i);
        String suffix = (i == path.size() - 1) ? constraint.to.id : s;
        s = String.format(DEPENDENCY_FORMAT, constraint.from.id, stripLayoutAttributePrefix(constraint.type.name), suffix);
      }

      if (newName != null) {
        s = String.format(DEPENDENCY_FORMAT, s, stripLayoutAttributePrefix(newName), newId != null ? LintUtils.stripIdPrefix(newId) : "?");
      }

      return s;
    }

    private static String stripLayoutAttributePrefix(String name) {
      if (name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
        return name.substring(ATTR_LAYOUT_RESOURCE_PREFIX.length());
      }

      return name;
    }
  }
}
