/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.studio.samples;

import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.appspot.gsamplesindex.samplesindex.model.SampleCollection;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;

/**
 * Sample Import Tree Manager manages the data structures required for a searchable sample list
 * grouped by category nodes.  A node that belongs to multiple categories will have multiple
 * associated nodes, one for each category.
 *
 * The tree is designed to look like :
 * root
 *  |-- category 1
 *  |    |-- sample A
 *  |    \-- sample B
 *  \-- category 2
 *       |-- sample A
 *       \-- sample C
 */
public class SampleImportTreeManager {

  private final SampleCollection mySamples;
  private final Tree myTree;
  private final Map<DefaultMutableTreeNode, Sample> mySampleMap = new HashMap<DefaultMutableTreeNode, Sample>();
  private final Map<String, String> myFormattedNameMap = new HashMap<String, String>();
  private final Map<String, DefaultMutableTreeNode> myCategoryMap = new TreeMap<String, DefaultMutableTreeNode>();
  private final Map<String, List<DefaultMutableTreeNode>> myCategorySampleNodeMap = new HashMap<String, List<DefaultMutableTreeNode>>();

  public SampleImportTreeManager(@NotNull Tree tree, @NotNull SampleCollection samples) {
    myTree = tree;
    mySamples = samples;
    init();
  }

  private void init() {
    // populate our data structures for tree update and filtering
    for (Sample sample : mySamples.getItems()) {
      if (StringUtil.isEmpty(sample.getTitle())) {
        continue;
      }
      String formattedName = formatName(sample.getTitle());
      myFormattedNameMap.put(sample.getTitle(),formattedName);
      for (String category : sample.getCategories()) {
        DefaultMutableTreeNode sampleNode = new DefaultMutableTreeNode(formattedName);
        mySampleMap.put(sampleNode, sample);
        if (!myCategoryMap.containsKey(category)) {
          DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(StringUtil.capitalize(category));
          myCategoryMap.put(category, categoryNode);
        }
        if (!myCategorySampleNodeMap.containsKey(category)) {
          myCategorySampleNodeMap.put(category, new ArrayList<DefaultMutableTreeNode>());
        }
        myCategorySampleNodeMap.get(category).add(sampleNode);
      }
    }

    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Samples");
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree.setModel(treeModel);
    myTree.setEditable(false);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    populateSamplesTree(mySamples.getItems(), true);
  }

  static String formatName(String name) {
    if (StringUtil.isEmpty(name) || name.trim().isEmpty()) {
      return "Unnamed";
    }
    name = name.replace("-"," - ");
    StringBuilder sb = new StringBuilder(2 * name.length());
    int n = name.length();
    boolean lastWasLowerCase = Character.isLowerCase(name.charAt(0));
    for (int i = 0; i < n; i++) {
      char c = name.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && lastWasLowerCase) {
        sb.append(' ');
      }
      lastWasLowerCase = Character.isLetter(c) && !isUpperCase;
      sb.append(c);
    }

    return sb.toString().replaceAll(" +", " ").trim();
  }

  /**
   * Create/Re-create the Sample tree UI view
   * @param samples
   * @param expand set to {@code true} to expand out all nodes
   */
  private void populateSamplesTree(@NotNull Collection<Sample> samples, boolean expand) {
    DefaultTreeModel model = (DefaultTreeModel) myTree.getModel();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    root.removeAllChildren();
    for (String category : myCategoryMap.keySet()) {
      DefaultMutableTreeNode categoryNode = myCategoryMap.get(category);
      categoryNode.removeAllChildren();
      for (DefaultMutableTreeNode node : myCategorySampleNodeMap.get(category)) {
        if (samples.contains(mySampleMap.get(node))) {
          myCategoryMap.get(category).add(node);
        }
      }
      if (categoryNode.getChildCount() > 0) {
        root.add(categoryNode);
      }
    }
    model.reload();
    if (expand) {
      for (int i = 0; i < myTree.getRowCount(); i++) {
        myTree.expandRow(i);
      }
      try {
        myTree.setSelectionPath(new TreePath(root.getFirstLeaf().getPath()));
      }
      catch (NoSuchElementException e) {
        // ignore selection
      }
    }
  }

  /**
   * Return the selected Sample or {@code null} if a non-sample node is selected.
   */
  @Nullable
  public Sample getSelectedSample() {
    DefaultMutableTreeNode selected = (DefaultMutableTreeNode) myTree.getLastSelectedPathComponent();
    if (selected == null || !selected.isLeaf()) {
      return null;
    }
    return mySampleMap.get(selected);
  }

  public boolean isEmpty() {
    return myTree.isEmpty();
  }

  /**
   * Filter through the samples based on a space separated set of keywords.
   * A list of Samples which have substring matches for ALL keywords in any of their
   * 1. title
   * 2. categories
   * 3. description
   * will be returned.
   */
  @NotNull
  Set<Sample> filterSamples(@NotNull String query) {
    Set<Sample> filteredSamples = new HashSet<Sample>(mySamples.getItems());
    Set<Sample> filterByWord = new HashSet<Sample>();
    for (String keyword : query.split(" ")) {
      filterByWord.clear();
      for (Sample sample : mySamples.getItems()) {
        if (hasKeyword(sample, keyword)) {
          filterByWord.add(sample);
        }
      }
      filteredSamples = Sets.intersection(filteredSamples, filterByWord).immutableCopy();
    }
    return filteredSamples;
  }

  private boolean hasKeyword(Sample sample, String keyword) {
    if (sample.getTitle() != null &&
        (StringUtil.containsIgnoreCase(sample.getTitle(), keyword) ||
         StringUtil.containsIgnoreCase(myFormattedNameMap.get(sample.getTitle()), keyword))) {
      return true;
    }
    if (sample.getDescription() != null && StringUtil.containsIgnoreCase(sample.getDescription(), keyword)) {
      return true;
    }
    for (String category : sample.getCategories()) {
      if (category != null && StringUtil.containsIgnoreCase(category, keyword)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Filter the tree based on a space separated set of keywords
   */
  public void filterTree(@NotNull String query) {
    populateSamplesTree(filterSamples(query), true);
  }
}
