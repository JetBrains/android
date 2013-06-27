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
import com.intellij.ui.Gray;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class NavigationEditorPanel2 extends JComponent {
  private static final Dimension GAP = new Dimension(150, 50);
  public static final Color BACKGROUND_COLOR = Color.LIGHT_GRAY; // new Color(192, 192, 192)
  public static final Color SNAP_GRID_LINE_COLOR_MINOR = Gray.get(180);
  public static final Color SNAP_GRID_LINE_COLOR_MIDDLE = Gray.get(170);
  public static final Color SNAP_GRID_LINE_COLOR_MAJOR = Gray.get(150);
  private static final Dimension SNAP_GRID = new Dimension(4, 4);
  private static final double SCALE = 0.333333;
  private static final EmptyBorder LABEL_BORDER = new EmptyBorder(0, 5, 0, 5);
  private static final Dimension ORIGINAL_SIZE = new Dimension(480, 800);
  private static final Dimension PREVIEW_SIZE = new Dimension((int)(ORIGINAL_SIZE.width * SCALE), (int)(ORIGINAL_SIZE.height * SCALE));
  private static final Dimension ARROW_HEAD_SIZE = new Dimension(10, 5);
  public static final Color LINE_COLOR = Color.GRAY;
  public static final Point MULTIPLE_DROP_STRIDE = new Point(50, 50);
  private static final String ID_PREFIX = "@+id/";

  private final NavigationModel myNavigationModel;
  private final Project myProject;
  private VirtualFileSystem myFileSystem;
  private String myPath;
  private final Map<State, AndroidRootComponent> myStateToComponent = new HashMap<State, AndroidRootComponent>();
  private final Map<AndroidRootComponent, State> myComponentToState = new HashMap<AndroidRootComponent, State>();
  private Selection mySelection = Selection.NULL;
  private Map<Transition, TransitionInfo> myTransitionToTransitionInfo = new IdentityHashMap<Transition, TransitionInfo>();
  private Map<Location, RenderedView> myLocationToRenderedView = null;
  private Image myBackgroundImage;

  static class Location {
    @NotNull
    public final State state;
    @NotNull
    public final String viewIdentifier;

    Location(@NotNull State state, @NotNull String viewIdentifier) {
      this.state = state;
      this.viewIdentifier = viewIdentifier;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Location location = (Location)o;

      if (!state.equals(location.state)) return false;
      if (!viewIdentifier.equals(location.viewIdentifier)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = state.hashCode();
      result = 31 * result + viewIdentifier.hashCode();
      return result;
    }
  }

  static class TransitionInfo {
    final Component myComponent;

    TransitionInfo(Component component) {
      this.myComponent = component;
    }
  }

  @Nullable
  static String getViewId(@Nullable RenderedView leaf) {
    if (leaf != null) {
      XmlTag tag = leaf.tag;
      if (tag != null) {
        String attributeValue = tag.getAttributeValue("android:id");
        if (attributeValue != null && attributeValue.startsWith(ID_PREFIX)) {
          return attributeValue.substring(ID_PREFIX.length());
        }
      }
    }
    return null;
  }

  private Map<Location, RenderedView> getLocationToRenderedView() {
    if (myLocationToRenderedView == null) {
      myLocationToRenderedView = new HashMap<Location, RenderedView>();
      for (final State state : getStates(myNavigationModel)) {
        AndroidRootComponent androidRootComponent = myStateToComponent.get(state);
        RenderedView root = androidRootComponent.getRenderResult().getHierarchy().getRoots().get(0);
        new Object() {
          void walk(RenderedView parent) {
            for (RenderedView child : parent.getChildren()) {
              String id = getViewId(child);
              if (id != null) {
                myLocationToRenderedView.put(new Location(state, id), child);
              }
              walk(child);
            }
          }
        }.walk(root);
      }
    }
    return myLocationToRenderedView;
  }

  private static void paintLeaf(Graphics g, @Nullable RenderedView leaf, Color color, AndroidRootComponent component) {
    if (leaf != null) {
      g.setColor(color);
      Rectangle r = component.getBounds(leaf);
      g.drawRect(r.x, r.y, r.width, r.height);
    }
  }

  private abstract static class Selection {

    private static Selection NULL = new EmptySelection();

    protected abstract void moveTo(Point location);

    protected abstract Selection finaliseSelectionLocation(Point location);

    protected abstract void paint(Graphics g);

    protected abstract void paintOver(Graphics g);

  }

  private Selection create(Point mouseDownLocation, boolean shiftDown) {
    Component component = getComponentAt(mouseDownLocation);
    if (component == this) {
      return Selection.NULL;
    }
    if (component instanceof AndroidRootComponent) {
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      if (!shiftDown) {
        return new AndroidRootComponentSelection(androidRootComponent, mouseDownLocation);
      }
      else {
        return new RelationSelection(androidRootComponent, mouseDownLocation);
      }
    }
    else {
      return new ComponentSelection<Component>(component, mouseDownLocation);
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
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }
  }

  class ComponentSelection<T extends Component> extends Selection {
    protected final Point myMouseDownLocation;
    protected final Point myOrigComponentLocation;
    protected final T myComponent;

    private ComponentSelection(T component, Point mouseDownLocation) {
      myComponent = component;
      myMouseDownLocation = mouseDownLocation;
      myOrigComponentLocation = myComponent.getLocation();
    }

    @Override
    protected void moveTo(Point location) {
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
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }
  }

  private class AndroidRootComponentSelection extends ComponentSelection<AndroidRootComponent> {
    private State myState;

    private AndroidRootComponentSelection(AndroidRootComponent component, Point mouseDownLocation) {
      super(component, mouseDownLocation);
      myState = myComponentToState.get(component);
    }

    @Override
    protected void moveTo(Point location) {
      Point newLocation = Utilities.add(Utilities.diff(location, myMouseDownLocation), myOrigComponentLocation);
      myComponent.setLocation(newLocation);
      myState.setLocation(Utilities.toNavPoint(newLocation));
      myNavigationModel.getListeners().notify(null);
    }
  }

  private class RelationSelection extends Selection {
    @NotNull private final AndroidRootComponent myComponent;
    @NotNull private Point myLocation;
    @Nullable private final RenderedView myLeaf;
    @Nullable private final RenderedView myNamedLeaf;

    private RelationSelection(@NotNull AndroidRootComponent component, @NotNull Point mouseDownLocation) {
      myComponent = component;
      myLocation = mouseDownLocation;
      Point p = component.convertPointFromViewToModel(mouseDownLocation);
      myLeaf = component.getRenderResult().getHierarchy().findLeafAt(p.x, p.y);
      myNamedLeaf = getNamedParent(myLeaf);
    }

    @Nullable
    private RenderedView getNamedParent(@Nullable RenderedView view) {
      while (view != null && getViewId(view) == null) {
        view = view.getParent();
      }
      return view;
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
    protected void paintOver(Graphics g) {
      paintLeaf(g, myLeaf, Color.RED, myComponent);
      paintLeaf(g, myNamedLeaf, Color.BLUE, myComponent);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      Component componentAt = getComponentAt(location);
      if (componentAt instanceof AndroidRootComponent) {
        if (myComponent != componentAt) {
          Map<AndroidRootComponent, State> m = myComponentToState;
          addRelation(m.get(myComponent), getViewId(myNamedLeaf), m.get(componentAt));
        }
      }
      return Selection.NULL;
    }

  }

  public NavigationEditorPanel2(Project project, VirtualFile file, NavigationModel navigationModel) {
    myProject = project;
    myFileSystem = file.getFileSystem();
    myPath = file.getParent().getParent().getPath();
    myNavigationModel = navigationModel;

    setBackground(BACKGROUND_COLOR);
    setForeground(LINE_COLOR);

    addChildren(getStates(navigationModel));
    addAllRelations();

    {
      MouseAdapter mouseListener = new MyMouseListener();
      addMouseListener(mouseListener);
      addMouseMotionListener(mouseListener);
    }

    {
      final DnDManager dndManager = DnDManager.getInstance();
      dndManager.registerTarget(new MyDnDTarget(), this);
    }
  }

  private static void addIfAbsent(Collection<State> result, Set<State> added, State state) {
    if (!added.contains(state)) {
      added.add(state);
      result.add(state);
    }
  }

  private static Collection<State> getStates(NavigationModel model) {
    Collection<State> result = new ArrayList<State>(); // use set and list to preserve order (for no particular reason)
    Set<State> added = new HashSet<State>();
    for (Transition t : model) {
      addIfAbsent(result, added, t.getSource());
      addIfAbsent(result, added, t.getDestination());
    }
    return result;
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

  private void drawGrid(Graphics g, Color c, int gridWidth, int gridHeight) {
    g.setColor(c);
    for (int x = 0; x < getWidth(); x += gridWidth) {
      g.drawLine(x, 0, x, getHeight());
    }
    for (int y = 0; y < getHeight(); y += gridHeight) {
      g.drawLine(0, y, getWidth(), y);
    }
  }

  private void drawBackground(Graphics g, int width, int height) {
    g.setColor(BACKGROUND_COLOR);
    g.fillRect(0, 0, width, height);

    drawGrid(g, SNAP_GRID_LINE_COLOR_MINOR, SNAP_GRID.width, SNAP_GRID.height);
    drawGrid(g, SNAP_GRID_LINE_COLOR_MIDDLE, SNAP_GRID.width * 5, SNAP_GRID.height * 5);
    drawGrid(g, SNAP_GRID_LINE_COLOR_MAJOR, SNAP_GRID.width * 10, SNAP_GRID.height * 10);
  }

  private Image getBackGroundImage() {
    if (myBackgroundImage == null || myBackgroundImage.getWidth(null) != getWidth() || myBackgroundImage.getHeight(null) != getHeight()) {
      myBackgroundImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
      drawBackground(myBackgroundImage.getGraphics(), getWidth(), getHeight());
    }
    return myBackgroundImage;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    // draw background
    g.drawImage(getBackGroundImage(), 0, 0, null);

    // draw component shadows
    for (Component c : myStateToComponent.values()) {
      Rectangle r = c.getBounds();
      ShadowPainter.drawRectangleShadow(g, r.x, r.y, r.width, r.height);
    }

    // draw selection
    mySelection.paint(g);
  }

  private void paintTransitions(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    Object oldRenderingHint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    //g.setColor(getForeground());
    g.setColor(Color.BLUE);
    for (Transition transition : myNavigationModel) {
      State source = transition.getSource();
      AndroidRootComponent sourceComponent = myStateToComponent.get(source);
      AndroidRootComponent destinationComponent = myStateToComponent.get(transition.getDestination());
      RenderedView sourceView = getRenderedView(transition);
      g.setColor(Color.BLUE);
      Rectangle r1 = sourceComponent.getBounds(sourceView);
      Rectangle r2 = myTransitionToTransitionInfo.get(transition).myComponent.getBounds();
      Rectangle r3 = destinationComponent.getBounds();

      g.drawRect(r1.x, r1.y, r1.width, r1.height);
      Point m1 = Utilities.centre(r1);
      Point m2 = Utilities.centre(r2);
      Point m3 = Utilities.centre(r3);

      Point A = new Point(m2.x, m1.y);
      Point B = new Point(m2.x, m3.y);

      Point p1 = Utilities.project(r1, A);
      Point p2 = Utilities.project(r2, A);

      Point p3 = Utilities.project(r2, B);
      Point p4 = Utilities.project(r3, B);

      g.drawLine(p1.x, p1.y, A.x, A.y);
      g.drawLine(A.x, A.y, p2.x, p2.y);
      g.drawLine(p3.x, p3.y, B.x, B.y);
      drawArrow(g, B.x, B.y, p4.x, p4.y);
    }
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldRenderingHint);
  }

  @NotNull
  private RenderedView getRenderedView(Transition transition) {
    State source = transition.getSource();
    RenderedView result = getLocationToRenderedView().get(new Location(source, transition.getViewIdentifier()));
    return result == null ? myStateToComponent.get(source).getRenderResult().getHierarchy().getRoots().get(0) : result;
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    paintTransitions(g);
    mySelection.paintOver(g);
  }

  private void addRelation(State source, @Nullable String viewIdentifier, State dest) {
    Transition transition = new Transition("", source, dest);
    transition.setViewIdentifier(viewIdentifier);
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
        myNavigationModel.getListeners().notify(null);
      }
    });
    c.setSelectedItem(gesture);
    c.setForeground(getForeground());
    //c.setBorder(LABEL_BORDER);
    //c.setOpaque(true);
    c.setBackground(BACKGROUND_COLOR);
    add(c);
    myTransitionToTransitionInfo.put(transition, new TransitionInfo(c));
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
        Component c = myTransitionToTransitionInfo.get(transition).myComponent;
        c.setSize(c.getPreferredSize());
        int sx = (sl.x + dl.x - c.getWidth()) / 2;
        int sy = (sl.y + dl.y - c.getHeight()) / 2;
        c.setLocation(sx, sy);
      }
    }
  }

  private void addChildrenOld(Collection<State> states) {
    final Set<State> visited = new HashSet<State>();
    final Point location = new Point(GAP.width, GAP.height);
    final Point maxLocation = new Point(0, 0);
    final int gridWidth = PREVIEW_SIZE.width + GAP.width;
    final int gridHeight = PREVIEW_SIZE.height + GAP.height;
    myStateToComponent.clear();
    myComponentToState.clear();
    for (State state : states) {
      if (visited.contains(state)) {
        continue;
      }
      new Object() {
        public void addChildrenFor(State source) {
          visited.add(source);
          add(createAndroidRootComponent(source, location));
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
      }.addChildrenFor(state);
    }
    setPreferredSize(new Dimension(maxLocation.x, maxLocation.y));
  }

  private void addChildren(Collection<State> states) {
    Point maxLocation = new Point(0, 0);
    Dimension gridSize = new Dimension(PREVIEW_SIZE.width + GAP.width, PREVIEW_SIZE.height + GAP.height);
    myStateToComponent.clear();
    myComponentToState.clear();
    for (State state : states) {
      Point location = Utilities.toAWTPoint(state.getLocation());
      add(createAndroidRootComponent(state, location));
      maxLocation = Utilities.max(maxLocation, location);
    }
    setPreferredSize(new Dimension(maxLocation.x + gridSize.width, maxLocation.y + gridSize.height));
  }

  private AndroidRootComponent createAndroidRootComponent(State state, Point point) {
    AndroidRootComponent result = new AndroidRootComponent();
    result.setScale(SCALE);
    VirtualFile file = myFileSystem.findFileByPath(myPath + "/layout/" + state.getXmlResourceName() + ".xml");
    if (file != null) {
      result.render(myProject, file);
    }
    result.setLocation(point);
    result.setSize(PREVIEW_SIZE);
    myStateToComponent.put(state, result);
    myComponentToState.put(result, state);
    return result;
  }

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
      Point location = mouseEvent.getPoint();
      setSelection(create(location, mouseEvent.isShiftDown()));
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
        Point dropLocation = aEvent.getPointOn(NavigationEditorPanel2.this);

        if (psiElements != null) {
          for (PsiElement element : psiElements) {
            if (element instanceof PsiQualifiedNamedElement) {
              PsiQualifiedNamedElement namedElement = (PsiQualifiedNamedElement)element;
              String qualifiedName = namedElement.getQualifiedName();
              if (qualifiedName != null) {
                State state = new State(qualifiedName);
                String name = namedElement.getName();
                if (name != null) {
                  state.setXmlResourceName(getXmlFileNameFromJavaFileName(name));
                }
                if (!myStateToComponent.containsKey(state)) {
                  add(createAndroidRootComponent(state, dropLocation));
                  dropLocation = Utilities.add(dropLocation, MULTIPLE_DROP_STRIDE);
                }
              }
            }
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
