/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypeAttribute;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypeCycle;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyTypePosition;
import static com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons.ADD_KEYFRAME;

class ViewList extends JPanel implements Gantt.ChartElement {
  DefaultMutableTreeNode myRootNode = new DefaultMutableTreeNode();
  JTree myTree = new Tree(myRootNode);
  Chart myChart;
  boolean myInternal;
  private static boolean USER_STUDY = true;
  private static final Icon mySpacerIcon = JBUI.scale(EmptyIcon.create(0, 0));

  JPanel myAddPanel = new JPanel(null) {
    @Override
    public void doLayout() {
      if (mySelectedView == null) {
        myAddButton.setVisible(false);
      }
      else {
        myAddButton.setVisible(true);
        myAddButton.setLocation(0, mySelectedView.getViewElement().myYStart);
        myAddButton.setSize(16, 16);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
    }
  };
  JButton myAddButton = new JButton(ADD_KEYFRAME);
  private ViewNode mySelectedView;

  ViewList(Chart chart) {
    super(new BorderLayout(2, 2));
    myChart = chart;
    myTree.setRootVisible(false);
    myTree.setCellRenderer(cellRenderer);
    update(Reason.CONSTRUCTION);
    setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 0, 0, 1));
    myChart.add(this);
    myAddPanel.add(myAddButton);
    myAddButton.setUI(new BasicButtonUI());
    myAddButton.setMargin(null);
    myAddButton.setBorderPainted(false);
    myAddButton.setOpaque(false);
    myAddButton.addActionListener((e) -> keyFramePopup(e));
    myAddPanel.setPreferredSize(new Dimension(16, 20));
    add(myTree, BorderLayout.CENTER);
    add(myAddPanel, BorderLayout.EAST);
    JPanel space = new JPanel();
    space.setPreferredSize(new Dimension(Chart.ourViewListWidth, 0));
    add(space, BorderLayout.NORTH);

    myTree.setUI(new CustomTreeUI());
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        myInternal = true;
        if (event.getPath().getPathCount() == 3) {
          CategoryNode categoryNode = (CategoryNode)(event.getPath().getPathComponent(2));
        }
        myChart.update(Reason.RESIZE);
        myInternal = false;
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        myInternal = true;
        myChart.update(Reason.RESIZE);
        myInternal = false;
      }
    });
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        myInternal = true;
        treeSelection(e);
        myInternal = false;
      }
    });
  }

  private void keyFramePopup(ActionEvent e) {
    String[] list = {"Position", "Attributes", "Cycles"};

    if (myChart != null
        && myChart.myModel != null) {
      boolean noStartConstraints = myChart.myModel.getStartConstraintSet().myConstraintViews.isEmpty();
      boolean noEndConstraints = myChart.myModel.getEndConstraintSet().myConstraintViews.isEmpty();
      if (noStartConstraints && noEndConstraints) {
        list = new String[] {"set Start Constraint", "set End Constraint"};
      } else if (noStartConstraints) {
        list = new String[] {"set Start Constraint"};
      } else if (noEndConstraints) {
        list = new String[] {"set End Constraint"};
      }
    }
    final JList<String> displayedList = new JBList<>(list);
    JBPopupListener listener = new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        JBPopup popup = event.asPopup();
        boolean noStartConstraints = myChart.myModel.getStartConstraintSet().myConstraintViews.isEmpty();
        boolean noEndConstraints = myChart.myModel.getEndConstraintSet().myConstraintViews.isEmpty();
        if (noStartConstraints && noEndConstraints) {
          if (displayedList.getSelectedIndex() == 0) {
            myChart.setCursorPosition(0);
          } else {
            myChart.setCursorPosition(1);
          }
        } else if (noStartConstraints) {
          myChart.setCursorPosition(0);
        } else if (noEndConstraints) {
          myChart.setCursorPosition(1);
        } else {
          createKeyFrame(displayedList.getSelectedIndex());
        }
      }
    };
    JBPopup popup =
      JBPopupFactory.getInstance()
                    .createListPopupBuilder(displayedList)
                    .setTitle("Create KeyFrame")
                    .addListener(listener).createPopup();

    JComponent component = ((JComponent)e.getSource());

    popup.show(new RelativePoint(component, new Point(0, 0)));
  }

  void createKeyFrame(int frameType) {
    Gantt.ViewElement v = mySelectedView.getViewElement();

    String name = v.myName;
    MotionSceneModel model = v.mKeyFrames.myModel;
    int fpos = (int)(myChart.getTimeCursorMs() * 100 / myChart.myAnimationTotalTimeMs);
    if (fpos == 0) {
       fpos = 1;
    } else if (fpos == 100) {
      fpos = 99;
    }
    String type = (new String[]{KeyTypePosition, KeyTypeAttribute, KeyTypeCycle})[frameType];

    v.mKeyFrames.myModel.createKeyFrame(type, fpos, name);
    myChart.delayedSelectKeyFrame(type,name,fpos);

  }

  void treeSelection(TreeSelectionEvent e) {
    mySelectedView = null;
    if (e.getPath().getPath().length > 1) {
      mySelectedView = (ViewNode)(e.getPath().getPath()[1]);
      myChart.mySelectedKeyView = e.getPath().getPath()[1].toString();
      myChart.update(Reason.SELECTION_CHANGED);
    }
    myAddPanel.doLayout();
  }

  private void graph(Chart.GraphElements toGraph) {
    myChart.myGraphElements = toGraph;
    myChart.update(Reason.GRAPH_SELECTED);
  }

  /* ========================Create the look of the tree============================= */

  public class CustomTreeUI extends BasicTreeUI {

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
      return new NodeDimensionsHandler() {
        @Override
        public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
          Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
          dimensions.width = getWidth() - getRowX(row, depth) - 1;
          return dimensions;
        }
      };
    }

    @Override
    protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {
      // do nothing.
    }

    @Override
    protected void paintVerticalPartOfLeg(Graphics g, Rectangle clipBounds, Insets insets, TreePath path) {
      // do nothing.
    }
  }

  TreeCellRenderer cellRenderer = new MyDefaultTreeCellRenderer();

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    if (myAddButton != null) {
      myAddPanel.setBackground(bg);
    }
    if (myTree != null) {
      myTree.setBackground(bg);
    }
  }

  /* ========================================================== */
  static class CategoryNode extends DefaultMutableTreeNode {
    enum Type {
      Position,
      Attributes,
      Cycles
    }

    Type myType;
    Gantt.ViewElement myView;
    ArrayList<? extends MotionSceneModel.KeyFrame> myKeyList;

    CategoryNode(Gantt.ViewElement viewElement, ArrayList<? extends MotionSceneModel.KeyFrame> keyList, Type type) {
      super((type == Type.Position) ? "Position" : ((type == Type.Attributes) ? "Attributes" : "Cycles"));
      myKeyList = keyList;
      myView = viewElement;
      myType = type;
      String[] list = MotionSceneModel.getGraphAttributes(keyList);
      Arrays.sort(list);
      switch (type) {
        case Position:
          viewElement.myHasPosition = true;
          break;
        case Attributes:
          viewElement.myHasAttribute = true;
          break;
        case Cycles:
          viewElement.myHasCycle = true;
          break;
      }
      for (int i = 0; i < list.length; i++) {
        String name = list[i];
        add(new GraphMode(MotionSceneModel.filterList(keyList, name), type, name));
      }
    }
  }
  /* ========================================================== */

  static class GraphMode extends DefaultMutableTreeNode {
    ArrayList<MotionSceneModel.KeyFrame> myKeyList;

    GraphMode(ArrayList<MotionSceneModel.KeyFrame> keyList, CategoryNode.Type type, String name) {
      super(name);
      myKeyList = keyList;
    }
  }
  /* ========================================================== */

  static class ViewNode extends DefaultMutableTreeNode {

    public ViewNode(Gantt.ViewElement element) {
      super(element);
    }

    Gantt.ViewElement getViewElement() {
      return (Gantt.ViewElement)super.userObject;
    }
  }
  /* ========================================================== */

  @Override
  public void update(Reason reason) {
    // TODO change a graph to be size of graph I want
    // TODO Change graph to be size from here
    if (reason == Reason.SELECTION_CHANGED) {
      updateSizes();
      return;
    }
    if (reason == Reason.ZOOM) {
      return;
    }
    if (!myInternal && reason == Reason.ADDVIEW) {
      HashSet<String> expanded = new HashSet<>();
      for (int i = 0; i < myTree.getRowCount() - 1; i++) {
        TreePath currPath = myTree.getPathForRow(i);
        TreePath nextPath = myTree.getPathForRow(i + 1);
        if (currPath.isDescendant(nextPath)) {
          expanded.add(Arrays.toString(currPath.getPath()));
        }
      }
      reload();
      for (int i = 0; i < myTree.getRowCount() - 1; i++) {
        TreePath currPath = myTree.getPathForRow(i);
        String s = Arrays.toString(currPath.getPath());
        if (expanded.contains(s)) {
          myTree.expandPath(currPath);
        }
      }
    }
    int count = myRootNode.getChildCount();

    updateSizes();

    TreePath[] selection = myTree.getSelectionPaths();
    if (selection != null && selection.length > 0 && selection[0].getPath().length > 1) {
      mySelectedView = (ViewNode)(selection[0].getPath()[1]);
    }
    else {
      mySelectedView = null;
    }
    myAddPanel.doLayout();
  }

  private void updateSizes() {

    int viewNo = 0;
    Gantt.ViewElement viewElement = null;
    CategoryNode categoryNode;

    for (int i = 0; i < myTree.getRowCount(); i++) {
      TreePath path = myTree.getPathForRow(i);
      Rectangle rectangle = myTree.getPathBounds(path);
      Object last = path.getLastPathComponent();
      if (last instanceof ViewNode) {
        viewElement = ((ViewNode)last).getViewElement();
        viewElement.myYStart = rectangle.y;
        viewElement.myHeight = rectangle.height;
        viewElement.myHeightView = rectangle.height;
        viewElement.myHeightCycle = 0;
        viewElement.myHeightAttribute = 0;
        viewElement.myHeightPosition = 0;
      }
      else if (last instanceof CategoryNode) {
        categoryNode = (CategoryNode)last;
        switch (categoryNode.myType) {
          case Cycles:
            viewElement.myHeightCycle = rectangle.height;
            break;
          case Attributes:
            viewElement.myHeightAttribute = rectangle.height;
            break;
          case Position:
            viewElement.myHeightPosition = rectangle.height;
            break;
        }
      }

      if (rectangle != null) {
        if (!(last instanceof ViewNode)) {
          viewElement.myHeight += rectangle.height;
        }
      }
    }
  }

  void reload() {
    myRootNode.removeAllChildren();
    DefaultMutableTreeNode node = myRootNode;
    DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();

    for (Gantt.ViewElement viewElement : myChart.myViewElements) {
      myRootNode.add(node = new ViewNode(viewElement));
      if (!viewElement.mKeyFrames.myKeyPositions.isEmpty()) {
        node.add(new CategoryNode(viewElement, viewElement.mKeyFrames.myKeyPositions, CategoryNode.Type.Position));
      }
      if (!viewElement.mKeyFrames.myKeyAttributes.isEmpty()) {
        node.add(new CategoryNode(viewElement, viewElement.mKeyFrames.myKeyAttributes, CategoryNode.Type.Attributes));
      }
      if (!viewElement.mKeyFrames.myKeyCycles.isEmpty()) {
        node.add(new CategoryNode(viewElement, viewElement.mKeyFrames.myKeyCycles, CategoryNode.Type.Cycles));
      }
    }

    model.reload();
  }

  private static class MyDefaultTreeCellRenderer extends DefaultTreeCellRenderer {
    public static final JBEmptyBorder TEXT_PADDING_BORDER = JBUI.Borders.empty(2, 0);

    MyDefaultTreeCellRenderer() {
      setBorder(TEXT_PADDING_BORDER);
      setBackgroundNonSelectionColor(StudioColorsKt.getSecondaryPanelBackground());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;

      JComponent c =
        (JComponent)super.getTreeCellRendererComponent(tree, (node.getChildCount() == 0) ? "" : value, sel, expanded, leaf, row, hasFocus);

      Object root = tree.getModel().getRoot();

      if (root.equals(node)) {
        setIcon(EmptyIcon.ICON_0);
        return c;
      }

      Object userObject = node.getUserObject();
      if (userObject instanceof Gantt.ViewElement) {
        setIcon(((Gantt.ViewElement)userObject).getIcon());
      }
      else if (node.getParent() == root) {
        setIcon(StudioIcons.LayoutEditor.Palette.VIEW);
      }
      else if (node instanceof GraphMode) {
        setIcon(mySpacerIcon);
      }
      else {
        setIcon(EmptyIcon.ICON_0);
      }
      setText(userObject.toString());
      setBackgroundSelectionColor(UIUtil.getTreeSelectionBackground(hasFocus));
      return c;
    }
  }
}
