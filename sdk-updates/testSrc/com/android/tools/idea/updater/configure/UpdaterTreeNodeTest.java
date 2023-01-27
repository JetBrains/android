/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Set;
import javax.swing.tree.TreeNode;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static com.android.SdkConstants.FD_NDK;
import static com.android.tools.idea.updater.configure.PackageNodeModel.SelectedState.*;
import static org.junit.Assert.*;

/**
 * Tests for {@link UpdaterTreeNode} and subclasses.
 */
public class UpdaterTreeNodeTest {
  @Mock private SdkUpdaterConfigurable myConfigurable;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testUpdaterTreeNode() throws Exception {
    // Installed cycles installed->not_installed->installed
    UpdaterTreeNode node = new TestTreeNode(INSTALLED, false);
    node.cycleState();
    validateState(node, NOT_INSTALLED, INSTALLED);
    node.cycleState();
    validateState(node, INSTALLED, INSTALLED);
    assertEquals(INSTALLED, node.getCurrentState());

    // Not installed cycles not_installed->installed->not_installed
    node = new TestTreeNode(NOT_INSTALLED, false);
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED);
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED);

    // If it can be mixed, it cycles not_installed->mixed->installed->not_installed
    node = new TestTreeNode(NOT_INSTALLED, true);
    node.cycleState();
    validateState(node, MIXED, NOT_INSTALLED);
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED);
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED);
  }

  @Test
  public void testDetailsTreeNodeTitle() throws Exception {
    FakePackage.FakeLocalPackage local = new FakePackage.FakeLocalPackage("foo");
    local.setDisplayName("my package");
    UpdatablePackage updatablePackage = new UpdatablePackage(local);
    DetailsTreeNode node = new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable);
    validateText(node, "my package", SimpleTextAttributes.REGULAR_ATTRIBUTES);

    local.setObsolete(true);
    node = new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable);
    validateText(node, "my package (Obsolete)", SimpleTextAttributes.REGULAR_ATTRIBUTES);

    // bug 133519160
    local = new FakePackage.FakeLocalPackage(FD_NDK);
    local.setDisplayName("legacy ndk");
    updatablePackage = new UpdatablePackage(local);
    node = new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable);
    validateText(node, "legacy ndk (Obsolete)", SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Test
  public void testDetailsTreeNodeStates() throws Exception {
    DetailsTreeNode node = createMultiVersionChild(true, false, "1");
    assertEquals(INSTALLED, node.getCurrentState());
    assertEquals(INSTALLED, node.getInitialState());

    node = createMultiVersionChild(true, true, "1");
    assertEquals(INSTALLED, node.getCurrentState());
    assertEquals(INSTALLED, node.getInitialState());

    node = createMultiVersionChild(false, true, "1");
    assertEquals(NOT_INSTALLED, node.getCurrentState());
    assertEquals(NOT_INSTALLED, node.getInitialState());

    node = createMultiVersionChild(true, true, "1");
    ((FakePackage.FakeRemotePackage)node.getItem().getRemote()).setRevision(new Revision(2));
    assertEquals(MIXED, node.getInitialState());
    assertTrue(node.canHaveMixedState());
  }

  @Test
  public void testDetailsTreeNodeListener() throws Exception {
    UpdatablePackage updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("foo"));
    ChangeListener listener = Mockito.mock(ChangeListener.class);
    DetailsTreeNode node = new DetailsTreeNode(new PackageNodeModel(updatablePackage), listener, myConfigurable);
    node.setState(NOT_INSTALLED);
    ArgumentCaptor<ChangeEvent> argument = ArgumentCaptor.forClass(ChangeEvent.class);
    Mockito.verify(listener).stateChanged(argument.capture());
    assertEquals(node, argument.getValue().getSource());
  }

  @Test
  public void testParentTreeNodeTitles() throws Exception {
    ParentTreeNode node = new ParentTreeNode(new AndroidVersion(10, null));
    validateText(node, "Android 2.3.3 (\"Gingerbread\")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    node = new ParentTreeNode(new AndroidVersion(99, "dessert of the future"));
    validateText(node, "Android dessert of the future Preview", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    node = new ParentTreeNode("some text");
    validateText(node, "some text", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  @Test
  public void testParentTreeNodeState() throws Exception {
    // All children are not installed. Cycling causes all children to be installed, and back.
    ParentTreeNode node = new ParentTreeNode("foo");
    addUnselectedChildren(node);
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED));

    // All children are installed. Cycling causes all children to be uninstalled, and back.
    node = new ParentTreeNode(new AndroidVersion(10, null));
    addSelectedChildren(node);
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(INSTALLED, INSTALLED));

    // Some children are installed, some not, and some have upgrades available.
    // Cycling causes all to be installed, not installed, and back.
    node = new ParentTreeNode(new AndroidVersion(10, null));
    addMixedChildren(node);
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED));
    node.cycleState();
    validateState(node, INSTALLED, MIXED, ImmutableList.of(INSTALLED, INSTALLED, INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED));
  }

  @Test
  public void testMultiVersionTreeNodeTitle() throws Exception {
    List<DetailsTreeNode> children = getMultiVersionChildren();
    MultiVersionTreeNode node = new MultiVersionTreeNode(children);
    children.forEach(node::add);
    validateText(node, "Foo", null);
  }

  @Test
  public void testMultiVersionTreeNodeState() throws Exception {
    // Some children are installed and some not. The latest version has an update available.
    // Cycling causes the latest to be installed, everything to be uninstalled, and back.
    List<DetailsTreeNode> children = getMultiVersionChildren();
    MultiVersionTreeNode node = new MultiVersionTreeNode(children);
    children.forEach(node::add);
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED));
    node.cycleState();
    validateState(node, INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED));

    // Some children are installed and some not. The latest version is not installed.
    // Cycling causes the latest to be installed, everything to be uninstalled, and back.
    children = getMultiVersionChildren();
    children.add(createMultiVersionChild(false, true, "3.0"));
    node = new MultiVersionTreeNode(children);
    children.forEach(node::add);
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, MIXED, MIXED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED, NOT_INSTALLED));

    // Some children are installed and some not. The latest version is installed.
    // Cycling causes everything to be uninstalled and back.
    children = getMultiVersionChildren();
    children.add(createMultiVersionChild(true, false,"3.0"));
    node = new MultiVersionTreeNode(children);
    children.forEach(node::add);
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, INSTALLED,
                  ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(NOT_INSTALLED, INSTALLED, INSTALLED, MIXED, INSTALLED));

    // All children are uninstalled. Cycling causes the latest to be installed and back.
    children = Lists.newArrayList(createMultiVersionChild(false, true, "1.0"),
                                  createMultiVersionChild(false, true, "2.0"));
    node = new MultiVersionTreeNode(children);
    children.forEach(node::add);
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED));
  }

  @Test
  public void testSummaryNodeTitles() throws Exception {
    TestTreeNode element = new TestTreeNode(INSTALLED, false);
    SummaryTreeNode node = SummaryTreeNode.createNode(new AndroidVersion(17, null), ImmutableSet.of(element));
    // since there are no included nodes, null is returned
    assertNull(node);

    element.setIncludeInSummary(true);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), ImmutableSet.of(element));
    validateText(node, "Android 4.2 (\"Jelly Bean\")", null);
  }

  @Test
  public void testSummaryTreeNodeState() throws Exception {
    //Nothing installed
    TestTreeNode n1 = new TestTreeNode(NOT_INSTALLED, false);
    n1.setIncludeInSummary(true);
    TestTreeNode n2 = new TestTreeNode(NOT_INSTALLED, false);
    n2.setIncludeInSummary(true);
    TestTreeNode n3 = new TestTreeNode(NOT_INSTALLED, false);
    ImmutableSet<UpdaterTreeNode> children = ImmutableSet.of(n1, n2, n3);
    SummaryTreeNode node = SummaryTreeNode.createNode(new AndroidVersion(17, null), children);
    children.forEach(node::add);
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));

    // Included not installed
    n3 = new TestTreeNode(INSTALLED, false);
    children = ImmutableSet.of(n1, n2, n3);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), children);
    children.forEach(node::add);
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, INSTALLED, INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, INSTALLED));

    // Included installed but others not
    n1 = new TestTreeNode(INSTALLED, false);
    n1.setIncludeInSummary(true);
    n2 = new TestTreeNode(INSTALLED, false);
    n2.setIncludeInSummary(true);
    n3 = new TestTreeNode(NOT_INSTALLED, false);
    children = ImmutableSet.of(n1, n2, n3);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), children);
    children.forEach(node::add);
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(INSTALLED, INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, INSTALLED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, INSTALLED, ImmutableList.of(INSTALLED, INSTALLED, NOT_INSTALLED));

    // Some included installed
    n2 = new TestTreeNode(NOT_INSTALLED, false);
    n2.setIncludeInSummary(true);
    children = ImmutableSet.of(n1, n2, n3);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), children);
    children.forEach(node::add);
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, NOT_INSTALLED, ImmutableList.of(INSTALLED, NOT_INSTALLED, NOT_INSTALLED));

    // Included upgradable
    n2 = new TestTreeNode(MIXED, true);
    n2.setIncludeInSummary(true);
    children = ImmutableSet.of(n1, n2, n3);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), children);
    children.forEach(node::add);
    validateState(node, MIXED, MIXED, ImmutableList.of(INSTALLED, MIXED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, INSTALLED, MIXED, ImmutableList.of(INSTALLED, INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, NOT_INSTALLED, MIXED, ImmutableList.of(NOT_INSTALLED, NOT_INSTALLED, NOT_INSTALLED));
    node.cycleState();
    validateState(node, MIXED, MIXED, ImmutableList.of(INSTALLED, MIXED, NOT_INSTALLED));
  }

  @Test
  public void testSummaryTreeNodeWithAndWithoutSources() throws Exception {
    DetailsTypes.PlatformDetailsType platformDetailsType = AndroidSdkHandler.getRepositoryModule().createLatestFactory()
      .createPlatformDetailsType();
    FakePackage.FakeRemotePackage platformPackage = new FakePackage.FakeRemotePackage("platform");
    FakePackage.FakeLocalPackage localPlatfromPackage = new FakePackage.FakeLocalPackage("platform");
    platformPackage.setTypeDetails((TypeDetails)platformDetailsType);
    localPlatfromPackage.setTypeDetails((TypeDetails)platformDetailsType);
    UpdatablePackage updatablePlatformPackage = new UpdatablePackage(localPlatfromPackage, platformPackage);
    DetailsTreeNode platformNode = new DetailsTreeNode(new PackageNodeModel(updatablePlatformPackage), null,
                                                       myConfigurable);
    Set<UpdaterTreeNode> nodes = ImmutableSet.of(platformNode);
    SummaryTreeNode node = SummaryTreeNode.createNode(new AndroidVersion(17, null), nodes);
    assertEquals("Installed", node.getStatusString());

    // Now create the sources node and add it without a local package - should imply partial installation status.
    DetailsTypes.SourceDetailsType sourceDetailsType = AndroidSdkHandler.getRepositoryModule().createLatestFactory()
      .createSourceDetailsType();
    FakePackage.FakeRemotePackage sourcesPackage = new FakePackage.FakeRemotePackage("sources");
    sourcesPackage.setTypeDetails((TypeDetails)sourceDetailsType);
    UpdatablePackage updatableSourcesPackage = new UpdatablePackage(sourcesPackage);
    DetailsTreeNode sourcesNode = new DetailsTreeNode(new PackageNodeModel(updatableSourcesPackage), null, myConfigurable);
    nodes = ImmutableSet.of(platformNode, sourcesNode);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), nodes);
    assertEquals("Partially installed", node.getStatusString());

    // Now test that both platform and sources installed imply the full installation status.
    FakePackage.FakeLocalPackage localSourcesPackage = new FakePackage.FakeLocalPackage("sources");
    localSourcesPackage.setTypeDetails((TypeDetails)sourceDetailsType);
    updatableSourcesPackage = new UpdatablePackage(localSourcesPackage, sourcesPackage);
    sourcesNode = new DetailsTreeNode(new PackageNodeModel(updatableSourcesPackage), null, myConfigurable);
    nodes = ImmutableSet.of(platformNode, sourcesNode);
    node = SummaryTreeNode.createNode(new AndroidVersion(17, null), nodes);
    assertEquals("Installed", node.getStatusString());
  }

  private static void validateText(@NotNull UpdaterTreeNode node, @NotNull String text, @Nullable SimpleTextAttributes attributes) {
    UpdaterTreeNode.Renderer renderer = Mockito.mock(UpdaterTreeNode.Renderer.class);
    ColoredTreeCellRenderer cellRenderer = Mockito.mock(ColoredTreeCellRenderer.class);
    Mockito.when(renderer.getTextRenderer()).thenReturn(cellRenderer);

    node.customizeRenderer(renderer, null, false, false, false, 0, false);
    if (attributes != null) {
      Mockito.verify(cellRenderer).append(text, attributes);
    }
    else {
      Mockito.verify(cellRenderer).append(text);
    }
  }

  private static void validateState(@NotNull UpdaterTreeNode node,
                                    @NotNull PackageNodeModel.SelectedState currentState,
                                    @NotNull PackageNodeModel.SelectedState initialState) {
    validateState(node, currentState, initialState, ImmutableList.of());
  }

  private static void validateState(@NotNull UpdaterTreeNode node,
                                    @NotNull PackageNodeModel.SelectedState currentState,
                                    @NotNull PackageNodeModel.SelectedState initialState,
                                    @NotNull List<PackageNodeModel.SelectedState> childStates) {
    assertEquals(currentState, node.getCurrentState());
    assertEquals(initialState, node.getInitialState());
    Iterator<PackageNodeModel.SelectedState> childStateIter = childStates.iterator();
    Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      assertEquals(childStateIter.next(), child.getCurrentState());
    }
    assertFalse(childStateIter.hasNext());
  }

  private void addUnselectedChildren(@NotNull UpdaterTreeNode parent) {
    UpdatablePackage updatablePackage = new UpdatablePackage(new FakePackage.FakeRemotePackage("remote1"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
    updatablePackage = new UpdatablePackage(new FakePackage.FakeRemotePackage("remote2"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
  }

  private void addMixedChildren(@NotNull UpdaterTreeNode parent) {
    UpdatablePackage updatablePackage = new UpdatablePackage(new FakePackage.FakeRemotePackage("remote"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
    updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("local"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
    updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("withRemote"),
                                            new FakePackage.FakeRemotePackage("withRemote"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
    FakePackage.FakeRemotePackage remote = new FakePackage.FakeRemotePackage("update");
    remote.setRevision(new Revision(2));
    updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("update"),
                                            remote);
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
  }

  private void addSelectedChildren(@NotNull UpdaterTreeNode parent) {
    UpdatablePackage updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("withoutRemote"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
    updatablePackage = new UpdatablePackage(new FakePackage.FakeLocalPackage("withRemote"),
                                            new FakePackage.FakeRemotePackage("withRemote"));
    parent.add(new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable));
  }

  @NotNull
  private List<DetailsTreeNode> getMultiVersionChildren() {
    List<DetailsTreeNode> result = new LinkedList<>();
    result.add(createMultiVersionChild(false, true, "1.0"));
    result.add(createMultiVersionChild(true, false, "1.5"));
    result.add(createMultiVersionChild(true, true, "1.5.1"));
    DetailsTreeNode child = createMultiVersionChild(true, true, "2.0");
    ((FakePackage.FakeRemotePackage)child.getItem().getRemote()).setRevision(new Revision(2));
    child.setState(child.getInitialState());
    result.add(child);
    return result;
  }

  @NotNull
  private DetailsTreeNode createMultiVersionChild(boolean hasLocal, boolean hasRemote, @NotNull String version) {
    assert hasLocal || hasRemote;
    FakePackage.FakeLocalPackage local;
    UpdatablePackage updatablePackage = null;
    if (hasLocal) {
      local = new FakePackage.FakeLocalPackage("foo;" + version);
      local.setDisplayName("Foo " + version);
      updatablePackage = new UpdatablePackage(local);
    }
    FakePackage.FakeRemotePackage remote;
    if (hasRemote) {
      remote = new FakePackage.FakeRemotePackage("foo;" + version);
      remote.setDisplayName("Foo " + version);
      if (updatablePackage != null) {
        updatablePackage.setRemote(remote);
      }
      else {
        updatablePackage = new UpdatablePackage(remote);
      }
    }
    return new DetailsTreeNode(new PackageNodeModel(updatablePackage), null, myConfigurable);
  }

  private static class TestTreeNode extends UpdaterTreeNode {
    private PackageNodeModel.SelectedState myInitialState;
    private PackageNodeModel.SelectedState myState;
    private boolean myCanHaveMixedState;
    private boolean myIncludeInSummary;

    public TestTreeNode(@NotNull PackageNodeModel.SelectedState initialState, boolean canHaveMixedState) {
      myInitialState = initialState;
      myState = initialState;
      myCanHaveMixedState = canHaveMixedState;
    }

    public void setIncludeInSummary(boolean include) {
      myIncludeInSummary = include;
    }

    @Override
    public boolean includeInSummary() {
      return myIncludeInSummary;
    }

    @Override
    @NotNull
    public PackageNodeModel.SelectedState getInitialState() {
      return myInitialState;
    }

    @Override
    @NotNull
    public PackageNodeModel.SelectedState getCurrentState() {
      return myState;
    }

    @Override
    protected void setState(@NotNull PackageNodeModel.SelectedState state) {
      myState = state;
    }

    @Override
    protected boolean canHaveMixedState() {
      return myCanHaveMixedState;
    }
  }
}
