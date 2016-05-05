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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintInspectorProvider;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InspectorPanel extends JPanel {
  private static final boolean DEBUG_BOUNDS = false;
  // TODO: We may want this size to depend on the actual font used instead of hardcoding it
  private static final int SPLIT_THRESHOLD_WIDTH = 220;

  private final Font myBoldLabelFont = UIUtil.getLabelFont().deriveFont(Font.BOLD);
  private final Icon myExpandedIcon;
  private final Icon myCollapsedIcon;
  private final JPanel myInspector;
  private final List<SplitComponents> mySplitComponents;
  private boolean mySplitComponentsOnSeparateLines;
  private List<InspectorComponent> myInspectors = Collections.emptyList();
  private List<Component> myGroup;
  private boolean myGroupInitiallyOpen;

  public InspectorPanel() {
    super(new BorderLayout());
    myExpandedIcon = (Icon)UIManager.get("Tree.expandedIcon");
    myCollapsedIcon = (Icon)UIManager.get("Tree.collapsedIcon");
    myInspector = new JPanel();
    mySplitComponents = new ArrayList<>();
    add(myInspector, BorderLayout.CENTER);
    myInspector.setLayout(createGridLayout());
    myInspector.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        boolean useSeparateLines = myInspector.getWidth() < SPLIT_THRESHOLD_WIDTH;
        if (useSeparateLines != mySplitComponentsOnSeparateLines) {
          mySplitComponents.stream().forEach(splitComponent -> splitComponent.addComponents(InspectorPanel.this, useSeparateLines));
          mySplitComponentsOnSeparateLines = useSeparateLines;
        }
      }
    });
  }

  private static LayoutManager createGridLayout() {
    // 5 column grid by default
    String layoutConstraints = "wrap 5, insets 2pt, novisualpadding, hidemode 3";
    if (DEBUG_BOUNDS) {
      layoutConstraints += ", debug";
    }
    // Dual configuration:
    // 1) [Single component] 1st column 30%, 2nd column 70%
    // 2) [Two components]   1st and 3rd column 20%, 2nd and 4th column 30%
    String columnConstraints = "[20%!][10%!][grow,fill][20%!][grow,fill]";

    return new MigLayout(layoutConstraints, columnConstraints);
  }

  public void setComponent(@NotNull List<NlComponent> components,
                           @NotNull Table<String, String, ? extends NlProperty> properties,
                           @NotNull NlPropertiesManager propertiesManager) {
    mySplitComponents.clear();
    myInspector.removeAll();
    myInspector.repaint();

    Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
    for (NlProperty property : properties.row(SdkConstants.ANDROID_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row(SdkConstants.AUTO_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }

    InspectorProvider[] allProviders = new InspectorProvider[]{
      new ConstraintInspectorProvider(),
      new IdInspectorProvider(),
      new TextInspectorProvider(),
      new FontInspectorProvider(),
    };

    List<InspectorComponent> inspectors = createInspectorComponents(components, propertiesManager, propertiesByName, allProviders);

    for (InspectorComponent inspector : inspectors) {
      inspector.attachToInspector(this);
    }

    endGroup();
    myInspectors = inspectors;
  }

  public void refresh() {
    ApplicationManager.getApplication().invokeLater(() -> myInspectors.stream().forEach(InspectorComponent::refresh));
  }

  @NotNull
  private static List<InspectorComponent> createInspectorComponents(@NotNull List<NlComponent> components,
                                                                    @NotNull NlPropertiesManager propertiesManager,
                                                                    @NotNull Map<String, NlProperty> properties,
                                                                    @NotNull InspectorProvider[] allProviders) {
    List<InspectorComponent> inspectors = Lists.newArrayListWithExpectedSize(allProviders.length);

    if (components.isEmpty()) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return ImmutableList.of(
        new IdInspectorProvider().createCustomInspector(Collections.<NlComponent>emptyList(), properties, propertiesManager));
    }

    for (InspectorProvider provider : allProviders) {
      if (provider.isApplicable(components, properties)) {
        inspectors.add(provider.createCustomInspector(components, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  public JLabel addExpandableTitle(@NotNull String title, @NotNull NlProperty groupStartProperty) {
    JLabel label = createLabel(title, null, null);
    label.setFont(myBoldLabelFont);
    addComponent(label, "span 5");

    startGroup(label, groupStartProperty);
    return label;
  }

  public void addSeparator() {
    endGroup();
    addComponent(new JSeparator(), "span 5, grow");
  }

  public JLabel addLabel(@NotNull String title) {
    JLabel label = createLabel(title, null, null);
    addComponent(label, "span 5");
    return label;
  }

  public JLabel addComponent(@NotNull String labelText,
                             @Nullable String tooltip,
                             @NotNull Component component) {
    JLabel label = createLabel(labelText, tooltip, component);
    addComponent(label, "span 2"); // 30%
    addComponent(component, "span 3"); // 70%
    return label;
  }

  public void addSplitComponents(@NotNull SplitLayout layout,
                                 @NotNull String labelText1,
                                 @Nullable String tooltip1,
                                 @NotNull Component component1,
                                 @NotNull String labelText2,
                                 @Nullable String tooltip2,
                                 @NotNull Component component2) {
    int index = myInspector.getComponentCount();
    JLabel label1 = createLabel(labelText1, tooltip1, component1);
    JLabel label2 = createLabel(labelText2, tooltip2, component2);
    SplitComponents split = new SplitComponents(index, layout, label1, component1, label2, component2);
    mySplitComponents.add(split);
    split.addComponents(this, false);
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public void addPanel(@NotNull JComponent panel) {
    addComponent(panel, "span 5, grow");
  }

  public void restartExpansionGroup() {
    assert myGroup != null;
    myGroup.stream().forEach(component -> component.setVisible(true));
    myGroup.clear();
  }

  private static JLabel createLabel(@NotNull String labelText, @Nullable String tooltip, @Nullable Component component) {
    JBLabel label = new JBLabel(labelText);
    label.setLabelFor(component);
    label.setToolTipText(tooltip);
    return label;
  }

  private void addComponent(@NotNull Component component, @NotNull String migConstraints) {
    addComponent(component, migConstraints, -1);
  }

  private void addComponent(@NotNull Component component, @NotNull String migConstraints, int index) {
    myInspector.add(component, migConstraints, index);
    if (myGroup != null) {
      myGroup.add(component);
      component.setVisible(myGroupInitiallyOpen);
    }
  }

  private void removeComponent(int index) {
    myInspector.remove(index);
  }

  private void startGroup(@NotNull JLabel label, @NotNull NlProperty groupStartProperty) {
    assert myGroup == null;
    List<Component> group = new ArrayList<>();
    String savedKey = "inspector.open." + groupStartProperty.getName();
    myGroupInitiallyOpen = PropertiesComponent.getInstance().getBoolean(savedKey);

    label.setIcon(myGroupInitiallyOpen ? myExpandedIcon : myCollapsedIcon);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        boolean wasExpanded = label.getIcon() == myExpandedIcon;
        label.setIcon(wasExpanded ? myCollapsedIcon : myExpandedIcon);
        group.stream().forEach(component -> component.setVisible(!wasExpanded));
        PropertiesComponent.getInstance().setValue(savedKey, !wasExpanded);
      }
    });

    myGroup = group;
  }

  private void endGroup() {
    myGroup = null;
  }

  public enum SplitLayout {SINGLE_ROW, STACKED, SEPARATE}

  private static class SplitComponents {
    private final int myIndex;
    private final SplitLayout myOriginalLayout;
    private final Component myLabel1;
    private final Component myComponent1;
    private final Component myLabel2;
    private final Component myComponent2;

    public SplitComponents(int index,
                           @NotNull SplitLayout layout,
                           @NotNull Component label1,
                           @NotNull Component component1,
                           @NotNull Component label2,
                           @NotNull Component component2) {
      myIndex = index;
      myOriginalLayout = layout;
      myLabel1 = label1;
      myComponent1 = component1;
      myLabel2 = label2;
      myComponent2 = component2;
    }

    public void addComponents(@NotNull InspectorPanel inspector, boolean onSeparateLines) {
      if (inspector.myInspector.getComponentCount() > myIndex) {
        inspector.removeComponent(myIndex);
        inspector.removeComponent(myIndex);
        inspector.removeComponent(myIndex);
        inspector.removeComponent(myIndex);
      }

      SplitLayout layout = onSeparateLines ? SplitLayout.SEPARATE : myOriginalLayout;
      switch (layout) {
        case SEPARATE:
          inspector.addComponent(myLabel1, "span 2", myIndex);
          inspector.addComponent(myComponent1, "span 3", myIndex + 1);
          inspector.addComponent(myLabel2, "span 2", myIndex + 2);
          inspector.addComponent(myComponent2, "span 3", myIndex + 3);
          break;

        case SINGLE_ROW:
          inspector.addComponent(myLabel1, "span 1", myIndex);
          inspector.addComponent(myComponent1, "span 2", myIndex + 1);
          inspector.addComponent(myLabel2, "span 1", myIndex + 2);
          inspector.addComponent(myComponent2, "span 1", myIndex + 3);
          break;

        case STACKED:
          inspector.addComponent(myLabel1, "span 3", myIndex);
          inspector.addComponent(myLabel2, "span 2", myIndex + 1);
          inspector.addComponent(myComponent1, "span 3", myIndex + 2);
          inspector.addComponent(myComponent2, "span 2", myIndex + 3);
          break;
      }
    }
  }
}
