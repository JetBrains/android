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
package com.android.tools.idea.updater.configure;

import com.android.repository.Revision;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.testframework.FakePackage;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link MultiVersionTreeNode}
 */
public class MultiVersionTreeNodeTest {
  @Test
  public void maxVersion() {
    SdkUpdaterConfigurable configurable = Mockito.mock(SdkUpdaterConfigurable.class);
    List<DetailsTreeNode> nodes = ImmutableList.of(
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.0-alpha1")), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.0-beta2")), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.0")), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;0.9.9")), false), null, configurable)
    );
    MultiVersionTreeNode node = new MultiVersionTreeNode(nodes);
    node.cycleState();
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(0).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(1).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.INSTALLED, nodes.get(2).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(3).getCurrentState());
  }

  @Test
  public void maxPreviewVersion() {
    SdkUpdaterConfigurable configurable = Mockito.mock(SdkUpdaterConfigurable.class);
    List<DetailsTreeNode> nodes = ImmutableList.of(
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.1-alpha2")), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.1-beta1")), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(new FakePackage.FakeRemotePackage("foo;1.0.0")), false), null, configurable)
    );
    MultiVersionTreeNode node = new MultiVersionTreeNode(nodes);
    node.cycleState();
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(0).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.INSTALLED, nodes.get(1).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(2).getCurrentState());
  }

  @Test
  public void latestVersion() {
    SdkUpdaterConfigurable configurable = Mockito.mock(SdkUpdaterConfigurable.class);
    FakePackage.FakeRemotePackage v1 = new FakePackage.FakeRemotePackage("foo;1");
    v1.setRevision(new Revision(1));
    FakePackage.FakeRemotePackage v11 = new FakePackage.FakeRemotePackage("foo;1.1");
    v11.setRevision(new Revision(1, 1));
    FakePackage.FakeRemotePackage latest = new FakePackage.FakeRemotePackage("foo;latest");
    latest.setRevision(new Revision(1, 1));

    List<DetailsTreeNode> nodes = ImmutableList.of(
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(v1), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(latest), false), null, configurable),
      new DetailsTreeNode(new PackageNodeModel(new UpdatablePackage(v11), false), null, configurable)
    );
    MultiVersionTreeNode node = new MultiVersionTreeNode(nodes);
    node.cycleState();
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(0).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.INSTALLED, nodes.get(1).getCurrentState());
    assertEquals(PackageNodeModel.SelectedState.NOT_INSTALLED, nodes.get(2).getCurrentState());
  }
}
