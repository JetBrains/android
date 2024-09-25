/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.common;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.graph.Traverser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates a set of targets, represented as Labels.
 *
 * <p>This class uses a tree to store the set of targets so that finding all the child targets of a
 * given directory is fast.
 */
public class TargetTree extends AbstractCollection<Label> {

  public static final TargetTree EMPTY = new TargetTree(Node.EMPTY);
  private static final Joiner PATH_JOINER = Joiner.on('/');

  private final Node root;

  private TargetTree(Node root) {
    this.root = root;
  }

  // Required for the Builder class to be used in an autovalue builder class:
  public static Builder builder() {
    return Builder.root();
  }

  /** Returns the set of labels at the given path, excluding any labels in child packages. */
  public Collection<Label> get(Path packagePath) {
    return root.find(packagePath.iterator())
        .map(LabelIterator::ofDirectTargets)
        .map(ImmutableSet::copyOf)
        .orElse(ImmutableSet.of());
  }

  @Override
  public Iterator<Label> iterator() {
    return LabelIterator.ofAllSubpackageTargets(root);
  }

  @Override
  public int size() {
    return root.size();
  }

  @Override
  public boolean isEmpty() {
    return root.isEmpty();
  }

  public TargetTree getSubpackages(Path pkg) {
    return root.find(pkg.iterator())
        .map(node -> Node.forPath(pkg, node))
        .map(TargetTree::new)
        .orElse(TargetTree.EMPTY);
  }

  private static class LabelIterator implements Iterator<Label> {

    private final Iterator<Node> nodes;
    private Node currentNode;
    private Iterator<String> targetNames;
    private Label next;

    static LabelIterator ofAllSubpackageTargets(Node root) {
      return new LabelIterator(
          Traverser.forTree(Node::children).depthFirstPreOrder(root).iterator());
    }

    static LabelIterator ofDirectTargets(Node root) {
      return new LabelIterator(Collections.singleton(root).iterator());
    }

    LabelIterator(Iterator<Node> nodes) {
      this.nodes = nodes;
      targetNames = Collections.emptyIterator();
      moveToNext();
    }

    private void moveToNext() {
      next = null;
      while (!targetNames.hasNext()) {
        if (!nodes.hasNext()) {
          return;
        }
        currentNode = nodes.next();
        targetNames = currentNode.targets().iterator();
      }
      next =
          Label.fromWorkspacePackageAndName(
              // TODO: b/334110669 - Consider multi workspace-builds.
              Label.ROOT_WORKSPACE,
              Path.of(PATH_JOINER.join(currentNode.path())),
              targetNames.next());
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public Label next() {
      Label current = next;
      moveToNext();
      return current;
    }
  }

  @AutoValue
  abstract static class Node {

    static final String ROOT_NAME = "";

    static final Node EMPTY =
        new AutoValue_TargetTree_Node(ROOT_NAME, ImmutableSet.of(), ImmutableSet.of());

    // We don't use the autovalue for this since it is initialized after instantiation, by the
    // parent node. It's impossible to have the parent & child set in the constructor.
    private Node parent;

    abstract String name();

    abstract ImmutableSet<String> targets();

    abstract ImmutableSet<Node> children();

    @Memoized
    public ImmutableMap<String, Node> childMap() {
      return Maps.uniqueIndex(children(), Node::name);
    }

    Iterator<String> path() {
      if (parent == null) {
        return Collections.emptyIterator();
      }
      return Iterators.concat(parent.path(), Collections.singleton(name()).iterator());
    }

    static Node create(String name, ImmutableSet<Node> children, ImmutableSet<String> content) {
      return new AutoValue_TargetTree_Node(name, content, children);
    }

    /** Constructs a new node for the given path with an existing node as its only child. */
    static Node forPath(Path path, Node child) {
      // iterate backwards through the path elements to construct the new nodes bottom up, as
      // required the the immutable data structure.
      // The parent is filled in upon it's construction, see create(...) above.
      for (int i = path.getNameCount() - 1; i >= 0; i--) {
        // when i == 0, we're creating a root node
        String name = i > 0 ? path.getName(i - 1).toString() : ROOT_NAME;
        child = Node.create(name, ImmutableSet.of(child), ImmutableSet.of());
      }
      return child;
    }

    @Memoized
    int size() {
      return targets().size() + children().stream().mapToInt(Node::size).sum();
    }

    @Memoized
    boolean isEmpty() {
      return targets().isEmpty() && children().stream().allMatch(Node::isEmpty);
    }

    Optional<Node> find(Iterator<Path> path) {
      if (!path.hasNext()) {
        return Optional.of(this);
      }
      String childKey = path.next().toString();
      Node child = childMap().get(childKey);
      if (child == null) {
        return Optional.empty();
      }
      return child.find(path);
    }
  }

  /** Builder for {@link TargetTree}. */
  public static class Builder {
    private final String name;
    private final ImmutableSet.Builder<String> content;
    private final Map<String, Builder> children = Maps.newHashMap();

    public static Builder root() {
      return new Builder(Node.ROOT_NAME);
    }

    private Builder(String name) {
      this.name = name;
      content = ImmutableSet.builder();
    }

    public TargetTree build() {
      return new TargetTree(buildNode());
    }

    Node buildNode() {
      ImmutableSet.Builder<Node> builder = ImmutableSet.builder();
      for (Builder childBuilder : children.values()) {
        builder.add(childBuilder.buildNode());
      }
      Node parent = Node.create(name, builder.build(), content.build());
      parent.children().forEach(child -> child.parent = parent);
      return parent;
    }

    @CanIgnoreReturnValue
    public Builder add(Label target) {
      return add(target.getPackage().iterator(), target.getName().toString());
    }

    @CanIgnoreReturnValue
    public Builder add(Iterator<Path> pkg, String targetName) {
      if (!pkg.hasNext()) {
        content.add(targetName);
        return this;
      }

      children.computeIfAbsent(pkg.next().toString(), Builder::new).add(pkg, targetName);
      return this;
    }
  }
}
