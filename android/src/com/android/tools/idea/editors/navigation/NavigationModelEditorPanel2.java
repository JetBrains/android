/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.navigation.NavigationModel;
import com.android.navigation.State;
import com.android.navigation.Transition;
import com.android.tools.idea.rendering.RenderedView;
import com.android.tools.idea.rendering.ShadowPainter;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ide.projectView.impl.TransferableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

public class NavigationModelEditorPanel2 extends JComponent {
  private static final Dimension GAP = new Dimension(150, 50);
  private static final double SCALE = 0.333333;
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 5, 0, 5);
  private static final Dimension ORIGINAL_SIZE = new Dimension(480, 800);
  private static final Dimension PREVIEW_SIZE = new Dimension((int)(ORIGINAL_SIZE.width * SCALE), (int)(ORIGINAL_SIZE.height * SCALE));
  private static final Dimension ARROW_HEAD_SIZE = new Dimension(10, 5);
  public static final Color LINE_COLOR = Color.GRAY;
  public static final Color BACKGROUND_COLOR = Color.LIGHT_GRAY;
  public static final Point MULTIPLE_DROP_STRIDE = new Point(50, 50);

  private final NavigationModel myNavigationModel;
  private final Project myProject;
  private VirtualFileSystem myFileSystem;
  private String myPath;
  private final Map<State, AndroidRootComponent> myStateToComponent = new HashMap<State, AndroidRootComponent>();
  private final Map<AndroidRootComponent, State> myComponentToState = new HashMap<AndroidRootComponent, State>();
  private Selection mySelection = Selection.NULL;
  private Map<Transition, Component> navigationToComponent = new IdentityHashMap<Transition, Component>();

  private abstract static class Selection {

    private static Selection NULL = new EmptySelection();

    protected abstract void moveTo(Point location);

    protected abstract Selection finaliseSelectionLocation(Point location);

    protected abstract void paint(Graphics g);

    private static Selection create(NavigationModelEditorPanel2 editor, Point mouseDownLocation, boolean relation) {
      Component component = editor.getComponentAt(mouseDownLocation);
      return component != editor ? !relation
                                   ? new ComponentSelection(component, mouseDownLocation)
                                   : new RelationSelection(editor, component, mouseDownLocation) : NULL;
    }
  }

  private static class EmptySelection extends Selection {
    @Override
    protected void moveTo(Point location) {
    }

    @Override
    protected void paint(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }
  }

  private static class ComponentSelection extends Selection {
    private final Point myMouseDownLocation;
    private final Point myOrigComponentLocation;
    private final Component myComponent;

    private ComponentSelection(Component component, Point mouseDownLocation) {
      myComponent = component;
      myMouseDownLocation = mouseDownLocation;
      myOrigComponentLocation = myComponent.getLocation();
    }

    @Override
    protected void moveTo(Point location) {
      myComponent.setLocation(Utilities.add(Utilities.diff(location, myMouseDownLocation), myOrigComponentLocation));
    }

    @Override
    protected void paint(Graphics g) {
      g.setColor(Color.BLUE);
      Rectangle selection = myComponent.getBounds();
      int l = 4;
      selection.grow(l, l);
      g.fillRoundRect(selection.x, selection.y, selection.width, selection.height, l, l);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }
  }

  private static class RelationSelection extends Selection {
    private final NavigationModelEditorPanel2 myOverViewPanel;
    private final Component myComponent;
    private Point myLocation;

    private RelationSelection(NavigationModelEditorPanel2 myNavigationModelEditorPanel2, Component component, Point mouseDownLocation) {
      myOverViewPanel = myNavigationModelEditorPanel2;
      myComponent = component;
      myLocation = mouseDownLocation;
    }

    @Override
    protected void moveTo(Point location) {
      myLocation = location;
    }

    @Override
    protected void paint(Graphics g) {
      g.setColor(LINE_COLOR);
      Point start = Utilities.centre(myComponent);
      drawArrow(g, start.x, start.y, myLocation.x, myLocation.y);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      Component componentAt = myOverViewPanel.getComponentAt(location);
      if (myComponent instanceof AndroidRootComponent && componentAt instanceof AndroidRootComponent) {
        myOverViewPanel.addRelation((AndroidRootComponent)myComponent, (AndroidRootComponent)componentAt);
      }
      return Selection.NULL;
    }

  }

  public NavigationModelEditorPanel2(Project project, VirtualFile file, NavigationModel navigationModel) {
    myProject = project;
    myFileSystem = file.getFileSystem();
    myPath = file.getParent().getParent().getPath();
    myNavigationModel = navigationModel;

    setBackground(BACKGROUND_COLOR);
    setForeground(LINE_COLOR);

    if (navigationModel.size() > 0) {
      addChildren(myNavigationModel.get(0).getSource());
    }
    addAllRelations();

    {
      MouseAdapter mouseListener = new MouseListener();
      addMouseListener(mouseListener);
      addMouseMotionListener(mouseListener);
    }

    {
      final DnDManager dndManager = DnDManager.getInstance();
      dndManager.registerTarget(new MyDnDTarget(), this);
    }
  }

  private void setSelection(Selection selection) {
    mySelection = selection;
    repaint();
  }

  private void moveSelection(Point location) {
    mySelection.moveTo(location);
    revalidate();
    repaint();
  }

  private void finaliseSelectionLocation(Point location) {
    mySelection = mySelection.finaliseSelectionLocation(location);
    revalidate();
    repaint();
  }

  private List<State> findDestinationsFor(State state, Set<State> exclude) {
    List<State> result = new ArrayList<State>();
    for (Transition transition : myNavigationModel) {
      State source = transition.getSource();
      if (source.equals(state)) {
        State destination = transition.getDestination();
        if (!exclude.contains(destination)) {
          result.add(destination);
        }
      }
    }
    return result;
  }

  private static void drawArrow(Graphics g1, int x1, int y1, int x2, int y2) {
    // x1 and y1 are coordinates of circle or rectangle
    // x2 and y2 are coordinates of circle or rectangle, to this point is directed the arrow
    Graphics2D g = (Graphics2D)g1.create();
    double dx = x2 - x1;
    double dy = y2 - y1;
    double angle = Math.atan2(dy, dx);
    int len = (int)Math.sqrt(dx * dx + dy * dy);
    AffineTransform t = AffineTransform.getTranslateInstance(x1, y1);
    t.concatenate(AffineTransform.getRotateInstance(angle));
    g.transform(t);
    g.drawLine(0, 0, len, 0);
    int basePosition = len - ARROW_HEAD_SIZE.width;
    int height = ARROW_HEAD_SIZE.height;
    g.fillPolygon(new int[]{len, basePosition, basePosition, len}, new int[]{0, -height, height, 0}, 4);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    g.setColor(BACKGROUND_COLOR);
    g.fillRect(0, 0, getWidth(), getHeight());

    Graphics2D g2d = (Graphics2D)g;
    Object oldRenderingHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(getForeground());
    for (Transition transition : myNavigationModel) {
      AndroidRootComponent sourceComponent = myStateToComponent.get(transition.getSource());
      AndroidRootComponent destinationComponent = myStateToComponent.get(transition.getDestination());
      Rectangle scb = sourceComponent.getBounds();
      Rectangle dcb = destinationComponent.getBounds();
      Point sc = Utilities.centre(scb);
      Point dc = Utilities.centre(dcb);
      Point scp = Utilities.project(scb, dc);
      Point dcp = Utilities.project(dcb, sc);
      drawArrow(g, scp.x, scp.y, dcp.x, dcp.y);
    }
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldRenderingHint);
    for (Component c : myStateToComponent.values()) {
      Rectangle r = c.getBounds();
      ShadowPainter.drawRectangleShadow(g, r.x, r.y, r.width, r.height);
    }
    mySelection.paint(g);
  }

  /*
  @Override
  protected void paintChildren(Graphics graphics) {
    //super.paintChildren(graphics);
  }
  */

  private void addRelation(@NotNull AndroidRootComponent srcComponent, @NotNull AndroidRootComponent destComp) {
    if (srcComponent == destComp) {
      return;
    }
    State source = myComponentToState.get(srcComponent);
    State dest = myComponentToState.get(destComp);
    Transition transition = new Transition("", source, dest);
    myNavigationModel.add(transition);
    addRelationView(transition);
  }

  private void addRelationView(final Transition transition) {
    String gesture = transition.getType();
    JComboBox c = new JComboBox(new Object[]{"", "click", "list", "menu", "contains"});
    c.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        transition.setType((String)itemEvent.getItem());
        myNavigationModel.listeners.notify(null);
      }
    });
    c.setSelectedItem(gesture);
    c.setForeground(getForeground());
    //c.setBorder(LABEL_BORDER);
    //c.setOpaque(true);
    c.setBackground(BACKGROUND_COLOR);
    add(c);
    navigationToComponent.put(transition, c);
  }

  private void addAllRelations() {
    for (Transition transition : myNavigationModel) {
      addRelationView(transition);
    }
  }

  @Override
  public void doLayout() {
    for (Transition transition : myNavigationModel) {
      AndroidRootComponent sourceComponent = myStateToComponent.get(transition.getSource());
      AndroidRootComponent destinationComponent = myStateToComponent.get(transition.getDestination());
      Point sl = Utilities.centre(sourceComponent);
      Point dl = Utilities.centre(destinationComponent);
      String gesture = transition.getType();
      if (gesture != null) {
        Component c = navigationToComponent.get(transition);
        c.setSize(c.getPreferredSize());
        int sx = (sl.x + dl.x - c.getWidth()) / 2;
        int sy = (sl.y + dl.y - c.getHeight()) / 2;
        c.setLocation(sx, sy);
      }
    }
  }

  private void addChildren(State root) {
    final Set<State> visited = new HashSet<State>();
    final Point location = new Point(GAP.width, GAP.height);
    final Point maxLocation = new Point(0, 0);
    final int gridWidth = PREVIEW_SIZE.width + GAP.width;
    final int gridHeight = PREVIEW_SIZE.height + GAP.height;
    myStateToComponent.clear();
    myComponentToState.clear();
    new Object() {
      public void addChildrenFor(State source) {
        visited.add(source);
        add(createActivityPanel(source, location));
        List<State> children = findDestinationsFor(source, visited);
        location.x += gridWidth;
        maxLocation.x = Math.max(maxLocation.x, location.x);
        if (children.isEmpty()) {
          location.y += gridHeight;
          maxLocation.y = Math.max(maxLocation.y, location.y);
        }
        else {
          for (State child : children) {
            addChildrenFor(child);
          }
        }
        location.x -= gridWidth;
      }
    }.addChildrenFor(root);
    setPreferredSize(new Dimension(maxLocation.x, maxLocation.y));
  }

  private AndroidRootComponent createActivityPanel(State state, Point location) {
    AndroidRootComponent result = new AndroidRootComponent();
    result.setScale(SCALE);
    VirtualFile file = myFileSystem.findFileByPath(myPath + "/layout/" + state.getXmlResourceName());
    result.render(myProject, file);
    result.setLocation(location);
    result.setSize(PREVIEW_SIZE);
    myStateToComponent.put(state, result);
    myComponentToState.put(result, state);
    return result;
  }

  private class MouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
      Point location = mouseEvent.getPoint();
      setSelection(Selection.create(NavigationModelEditorPanel2.this, location, mouseEvent.isShiftDown()));
      if (mySelection instanceof RelationSelection) {
        RelationSelection selection = (RelationSelection)mySelection;
        Component component = selection.myComponent;
        if (component instanceof AndroidRootComponent) {
          AndroidRootComponent rootComponent = (AndroidRootComponent)component;
          int dx0 = location.x - rootComponent.getX();
          int dy0 = location.y - rootComponent.getY();
          int dx = (int)(dx0 / SCALE * 2 / 1.5);
          int dy = (int)(dy0 / SCALE * 2 / 1.5);
          RenderedView leaf = rootComponent.getRenderResult().getHierarchy().findLeafAt(dx, dy);
          if (leaf != null) {
            XmlTag tag = leaf.tag;
            String attributeValue = tag.getAttributeValue("android:id");
            System.out.println("attributeValue = " + attributeValue);
          }
        }
      }
    }

    /*
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
      moveSelection(mouseEvent.getPoint());
    }
    */

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
      moveSelection(mouseEvent.getPoint());
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
      finaliseSelectionLocation(mouseEvent.getPoint());
    }
  }

  private class MyDnDTarget implements DnDTarget {

    @Override
    public boolean update(DnDEvent aEvent) {
      /*
      setHoverIndex(-1);
      if (aEvent.getAttachedObject() instanceof PaletteItem) {
        setDropTargetIndex(locationToTargetIndex(aEvent.getPoint()));
        aEvent.setDropPossible(true);
      }
      else {
        setDropTargetIndex(-1);
        aEvent.setDropPossible(false);
      }
      */
      aEvent.setDropPossible(true);
      //System.out.println("aEvent = " + aEvent);
      return false;
    }

    @Override
    public void drop(DnDEvent aEvent) {
      /*
      setDropTargetIndex(-1);
      if (aEvent.getAttachedObject() instanceof PaletteItem) {
        int index = locationToTargetIndex(aEvent.getPoint());
        if (index >= 0) {
          myGroup.handleDrop(myProject, (PaletteItem) aEvent.getAttachedObject(), index);
        }
      }
      */
      Object attachedObject = aEvent.getAttachedObject();
      if (attachedObject instanceof TransferableWrapper) {
        TransferableWrapper wrapper = (TransferableWrapper)attachedObject;
        PsiElement[] psiElements = wrapper.getPsiElements();
        Point dropLocation = aEvent.getPointOn(NavigationModelEditorPanel2.this);
        for (PsiElement element : psiElements) {
          if (element instanceof PsiQualifiedNamedElement) {
            PsiQualifiedNamedElement namedElement = (PsiQualifiedNamedElement)element;
            State state = new State(namedElement.getQualifiedName());
            state.setXmlResourceName(getXmlFileNameFromJavaFileName(namedElement.getName()));
            add(createActivityPanel(state, dropLocation));
            dropLocation = Utilities.add(dropLocation, MULTIPLE_DROP_STRIDE);
          }
        }
        revalidate();
        repaint();
      }
    }

    @Override
    public void cleanUpOnLeave() {
      //setDropTargetIndex(-1);
    }

    @Override
    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
      //System.out.println("image = " + image);
    }
  }

  private static String getXmlFileNameFromJavaFileName(String name) {
    //if (name.contains("ListFragment")) {
    //    return "";
    //}

    return Utilities.getXmlFileNameFromJavaFileName(name);
  }

}
