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

import com.android.navigation.*;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedView;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.ShadowPainter;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.Point;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.editors.navigation.Utilities.*;

public class NavigationEditorPanel2 extends JComponent {
  private static final Dimension GAP = new Dimension(150, 50);
  private static final Color BACKGROUND_COLOR = Gray.get(192);
  private static final Color SNAP_GRID_LINE_COLOR_MINOR = Gray.get(180);
  private static final Color SNAP_GRID_LINE_COLOR_MIDDLE = Gray.get(170);
  private static final Color SNAP_GRID_LINE_COLOR_MAJOR = Gray.get(160);

  // Snap grid
  private static final int MINOR_SNAP = 4;
  private static final int MIDDLE_COUNT = 5;
  private static final int MAJOR_COUNT = 10;

  public static final Dimension MINOR_SNAP_GRID = new Dimension(MINOR_SNAP, MINOR_SNAP);
  public static final Dimension MIDDLE_SNAP_GRID = scale(MINOR_SNAP_GRID, MIDDLE_COUNT);
  public static final Dimension MAJOR_SNAP_GRID = scale(MINOR_SNAP_GRID, MAJOR_COUNT);

  private static final int LINE_WIDTH = 3;
  private static final Point MULTIPLE_DROP_STRIDE = point(MAJOR_SNAP_GRID);
  private static final String ID_PREFIX = "@+id/";
  private static final Color TRANSITION_LINE_COLOR = new Color(80, 80, 255);
  private static final Condition<Component> SCREENS = instanceOf(AndroidRootComponent.class);
  private static final Condition<Component> EDITORS = not(SCREENS);

  private final RenderingParameters myMyRenderingParams;
  private final VirtualFile myFile;
  private final NavigationModel myNavigationModel;

  private final Assoc<State, AndroidRootComponent> myStateComponentAssociation = new Assoc<State, AndroidRootComponent>();
  private final Assoc<Transition, Component> myTransitionEditorAssociation = new Assoc<Transition, Component>();

  private boolean myStateCacheIsValid;
  private boolean myTransitionEditorCacheIsValid;
  @NotNull private Selections.Selection mySelection = Selections.NULL;
  private Map<State, Map<String, RenderedView>> myLocationToRenderedView = new IdentityHashMap<State, Map<String, RenderedView>>();
  private Image myBackgroundImage;
  private Point myMouseLocation;
  private float myScale = 1 / 4f;

  // Configuration

  private boolean showRollover = false;

  @Nullable
  private static RenderingParameters getRenderingParams(Project project, VirtualFile file) {
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        AndroidFacet facet = AndroidFacet.getInstance(psiFile);
        if (facet != null) {
          Configuration configuration = facet.getConfigurationManager().getConfiguration(file);
          return new RenderingParameters(project, configuration, facet);
        }
      }
    }
    return null;
  }

  public NavigationEditorPanel2(Project project, VirtualFile file, NavigationModel model) {
    myMyRenderingParams = getRenderingParams(project, file);
    myFile = file;
    myNavigationModel = model;

    setFocusable(true);
    setBackground(BACKGROUND_COLOR);
    setLayout(null);

    // Mouse listener
    {
      MouseAdapter mouseListener = new MyMouseListener();
      addMouseListener(mouseListener);
      addMouseMotionListener(mouseListener);
    }

    // Focus listener
    {
      addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent focusEvent) {
          repaint();
        }

        @Override
        public void focusLost(FocusEvent focusEvent) {
          repaint();
        }
      });
    }

    // Drag and Drop listener
    {
      final DnDManager dndManager = DnDManager.getInstance();
      dndManager.registerTarget(new MyDnDTarget(), this);
    }

    // Key listeners
    {
      Action remove = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          mySelection.remove();
          setSelection(Selections.NULL);
        }
      };
      registerKeyBinding(KeyEvent.VK_DELETE, "delete", remove);
      registerKeyBinding(KeyEvent.VK_BACK_SPACE, "backspace", remove);
    }

    // Model listener
    {
      myNavigationModel.getListeners().add(new Listener<NavigationModel.Event>() {
        @Override
        public void notify(@NotNull NavigationModel.Event event) {
          if (event.operation != NavigationModel.Event.Operation.UPDATE) {
            if (event.operandType.isAssignableFrom(State.class)) {
              myStateCacheIsValid = false;
            }
            if (event.operandType.isAssignableFrom(Transition.class)) {
              myTransitionEditorCacheIsValid = false;
            }
          }
          repaint();
        }
      });
    }
  }

  @Nullable
  Transition getTransition(AndroidRootComponent sourceComponent, @Nullable RenderedView namedSourceLeaf, Point mouseUpLocation) {
    Component destComponent = getComponentAt(mouseUpLocation);
    if (sourceComponent != destComponent) {
      if (destComponent instanceof AndroidRootComponent) {
        AndroidRootComponent destinationRoot = (AndroidRootComponent)destComponent;
        RenderedView endLeaf = getRenderedView(destinationRoot, mouseUpLocation);
        RenderedView namedEndLeaf = getNamedParent(endLeaf);

        Map<AndroidRootComponent, State> rootComponentToState = getStateComponentAssociation().valueToKey;
        Locator sourceLocator = Locator.of(rootComponentToState.get(sourceComponent), getViewId(namedSourceLeaf));
        Locator destinationLocator = Locator.of(rootComponentToState.get(destComponent), getViewId(namedEndLeaf));
        return new Transition("", sourceLocator, destinationLocator);
      }
    }
    return null;
  }

  public Rectangle getNamedLeafBoundsAt(Component sourceComponent, Point location) {
    Component destComponent = getComponentAt(location);
    if (sourceComponent != destComponent) {
      if (destComponent instanceof AndroidRootComponent) {
        AndroidRootComponent destinationRoot = (AndroidRootComponent)destComponent;
        RenderedView endLeaf = getRenderedView(destinationRoot, location);
        RenderedView namedEndLeaf = getNamedParent(endLeaf);
        return destinationRoot.getBounds(namedEndLeaf);
      }
    }
    return new Rectangle(location);
  }

  public float getScale() {
    return myScale;
  }

  public void setScale(float scale) {
    myScale = scale;
    repaint();
  }

  private Assoc<State, AndroidRootComponent> getStateComponentAssociation() {
    if (!myStateCacheIsValid) {
      syncStateCache(myStateComponentAssociation);
      myStateCacheIsValid = true;
    }
    return myStateComponentAssociation;
  }

  private Assoc<Transition, Component> getTransitionEditorAssociation() {
    if (!myTransitionEditorCacheIsValid) {
      syncTransitionCache(myTransitionEditorAssociation);
      myTransitionEditorCacheIsValid = true;
    }
    return myTransitionEditorAssociation;
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

  @Nullable
  static RenderedView getRenderedView(AndroidRootComponent component, Point mouseDownLocation) {
    Point p = component.convertPointFromViewToModel(diff(mouseDownLocation, component.getLocation()));
    RenderResult renderResult = component.getRenderResult();
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    return hierarchy != null ? hierarchy.findLeafAt(p.x, p.y) : null;
  }

  @Nullable
  static RenderedView getNamedParent(@Nullable RenderedView view) {
    while (view != null && getViewId(view) == null) {
      view = view.getParent();
    }
    return view;
  }

  @Nullable
  private static RenderedView getRootView(AndroidRootComponent androidRootComponent) {
    RenderResult renderResult = androidRootComponent.getRenderResult();
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return null;
    }
    List<RenderedView> roots = hierarchy.getRoots();
    if (roots.isEmpty()) {
      return null;
    }
    return roots.get(0);
  }

  private Map<String, RenderedView> getNameToRenderedView(State state) {
    Map<String, RenderedView> result = myLocationToRenderedView.get(state);
    if (result == null) {
      RenderedView root = getRootView(getStateComponentAssociation().keyToValue.get(state));
      if (root != null) {
        myLocationToRenderedView.put(state, result = createViewNameToRenderedView(root));
      }
      else {
        return Collections.emptyMap(); // rendering library hasn't loaded, temporarily return an empty map
      }
    }
    return result;
  }

  private static Map<String, RenderedView> createViewNameToRenderedView(@NotNull RenderedView root) {
    final Map<String, RenderedView> result = new HashMap<String, RenderedView>();
    new Object() {
      void walk(RenderedView parent) {
        for (RenderedView child : parent.getChildren()) {
          String id = getViewId(child);
          if (id != null) {
            result.put(id, child);
          }
          walk(child);
        }
      }
    }.walk(root);
    return result;
  }

  static void paintLeaf(Graphics g, @Nullable RenderedView leaf, Color color, AndroidRootComponent component) {
    if (leaf != null) {
      Color oldColor = g.getColor();
      g.setColor(color);
      drawRectangle(g, component.getBounds(leaf));
      g.setColor(oldColor);
    }
  }

  private void registerKeyBinding(int keyCode, String name, Action action) {
    InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), name);
    getActionMap().put(name, action);
  }

  private void setSelection(@NotNull Selections.Selection selection) {
    mySelection = selection;
    // the re-validate() call shouldn't be necessary but removing it causes orphaned
    // combo-boxes to remain visible (and click-able) a 'remove' operation
    revalidate();
    repaint();
  }

  private void moveSelection(Point location) {
    mySelection.moveTo(location);
    revalidate();
    repaint();
  }

  private void setMouseLocation(Point mouseLocation) {
    myMouseLocation = mouseLocation;
    if (showRollover) {
      repaint();
    }
  }

  private void finaliseSelectionLocation(Point location) {
    mySelection = mySelection.finaliseSelectionLocation(location);
    revalidate();
    repaint();
  }

  /*
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
  */

  private void drawGrid(Graphics g, Color c, Dimension size, int width, int height) {
    g.setColor(c);
    for (int x = 0; x < width; x += size.width) {
      g.drawLine(x, 0, x, getHeight());
    }
    for (int y = 0; y < height; y += size.height) {
      g.drawLine(0, y, getWidth(), y);
    }
  }

  private void drawBackground(Graphics g, int width, int height) {
    g.setColor(BACKGROUND_COLOR);
    g.fillRect(0, 0, width, height);

    drawGrid(g, SNAP_GRID_LINE_COLOR_MINOR, MINOR_SNAP_GRID, width, height);
    drawGrid(g, SNAP_GRID_LINE_COLOR_MIDDLE, MIDDLE_SNAP_GRID, width, height);
    drawGrid(g, SNAP_GRID_LINE_COLOR_MAJOR, MAJOR_SNAP_GRID, width, height);
  }

  private Image getBackGroundImage() {
    if (myBackgroundImage == null ||
        myBackgroundImage.getWidth(null) != getWidth() ||
        myBackgroundImage.getHeight(null) != getHeight()) {
      myBackgroundImage = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
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
    for (Component c : getStateComponentAssociation().keyToValue.values()) {
      Rectangle r = c.getBounds();
      ShadowPainter.drawRectangleShadow(g, r.x, r.y, r.width, r.height);
    }

    // draw selection
    mySelection.paint(g, hasFocus());
  }

  public static Graphics2D createLineGraphics(Graphics g) {
    Graphics2D g2D = (Graphics2D)g.create();
    g2D.setColor(TRANSITION_LINE_COLOR);
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2D.setStroke(new BasicStroke(LINE_WIDTH));
    return g2D;
  }

  private static Rectangle getCorner(int cornerDiameter, Point a) {
    int cornerRadius = cornerDiameter / 2;
    return new Rectangle(a.x - cornerRadius, a.y - cornerRadius, cornerDiameter, cornerDiameter);
  }

  private static void drawLine(Graphics g, Point a, Point b) {
    g.drawLine(a.x, a.y, b.x, b.y);
  }

  private static void drawArrow(Graphics g, Point a, Point b) {
    Utilities.drawArrow(g, a.x, a.y, b.x, b.y);
  }

  private static void drawRectangle(Graphics g, Rectangle r) {
    g.drawRect(r.x, r.y, r.width, r.height);
  }

  public void drawTransition(Graphics g, Rectangle src, Rectangle dst) {
    // draw source rect
    drawRectangle(g, src);

    // draw curved 'Manhattan route' from source to destination

    Point midSrc = Utilities.centre(src);
    Point midDst = Utilities.centre(dst);
    Point midMid = midpoint(midSrc, midDst);

    int dx = Math.abs(midSrc.x - midDst.x);
    int dy = Math.abs(midSrc.y - midDst.y);
    boolean horizontal = dx >= dy;

    Point A = horizontal ? new Point(midMid.x, midSrc.y) : new Point(midSrc.x, midMid.y);
    Point B = horizontal ? new Point(midMid.x, midDst.y) : new Point(midDst.x, midMid.y);

    Point pj0 = Utilities.project(A, src);
    Point pj5 = Utilities.project(B, dst);

    Dimension pathSize = dimension(diff(pj5, pj0));

    int cornerDiameter = Math.min(Math.min(pathSize.width, pathSize.height), Math.min(MAJOR_SNAP_GRID.width, MAJOR_SNAP_GRID.height));
    int cornerRadius = cornerDiameter / 2;

    Rectangle cornerA = getCorner(cornerDiameter, A);
    Rectangle cornerB = getCorner(cornerDiameter, B);

    Point pj1 = Utilities.project(pj0, cornerA);
    Point pj2 = Utilities.project(B, cornerA);
    Point pj3 = Utilities.project(A, cornerB);
    Point pj4 = Utilities.project(pj5, cornerB);

    drawLine(g, pj0, pj1);
    drawCorner(g, cornerDiameter, cornerRadius, pj1, pj2, horizontal);
    drawLine(g, pj2, pj3);
    drawCorner(g, cornerDiameter, cornerRadius, pj3, pj4, !horizontal);
    if (length(diff(pj4, pj5)) > 1) { //
      drawArrow(g, pj4, pj5);
    }

    // draw destination rect
    Color oldColor = g.getColor();
    g.setColor(Color.CYAN);
    drawRectangle(g, dst);
    g.setColor(oldColor);
  }

  public void paintTransitions(Graphics g) {
    for (Transition transition : myNavigationModel.getTransitions()) {
      drawTransition(g, getBounds(transition.getSource()), getBounds(transition.getDestination()));
    }
  }

  private static int angle(Point p) {
    //if ((p.x == 0) == (p.y == 0)) {
    //  throw new IllegalArgumentException();
    //}
    return p.x > 0 ? 0 : p.y < 0 ? 90 : p.x < 0 ? 180 : 270;
  }

  private static void drawCorner(Graphics g, int cornerDiameter, int cornerRadius, Point a, Point b, boolean horizontal) {
    Point centre = horizontal ? new Point(a.x, b.y) : new Point(b.x, a.y);
    int startAngle = angle(diff(a, centre));
    int endAngle = angle(diff(b, centre));
    int dangle = endAngle - startAngle;
    int angle = dangle - (Math.abs(dangle) <= 180 ? 0 : 360 * sign(dangle));
    g.drawArc(centre.x - cornerRadius, centre.y - cornerRadius, cornerDiameter, cornerDiameter, startAngle, angle);
  }

  private RenderedView getRenderedView(Locator locator) {
    return getNameToRenderedView(locator.getState()).get(locator.getViewName());
  }

  private void paintRollover(Graphics2D lineGraphics) {
    if (myMouseLocation == null || !showRollover) {
      return;
    }
    Component component = getComponentAt(myMouseLocation);
    if (component instanceof AndroidRootComponent) {
      Stroke oldStroke = lineGraphics.getStroke();
      lineGraphics.setStroke(new BasicStroke(1));
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      RenderedView leaf = getRenderedView(androidRootComponent, myMouseLocation);
      RenderedView namedLeaf = getNamedParent(leaf);
      paintLeaf(lineGraphics, leaf, Color.RED, androidRootComponent);
      paintLeaf(lineGraphics, namedLeaf, Color.BLUE, androidRootComponent);
      lineGraphics.setStroke(oldStroke);
    }
  }

  private void paintSelection(Graphics g) {
    mySelection.paintOver(g);
  }

  private void paintChildren(Graphics g, Condition<Component> condition) {
    Rectangle bounds = new Rectangle();
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      Component child = getComponent(i);
      if (condition.value(child)) {
        child.getBounds(bounds);
        Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
        child.paint(cg);
      }
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    paintChildren(g, SCREENS);
    Graphics2D lineGraphics = createLineGraphics(g);
    paintTransitions(lineGraphics);
    paintRollover(lineGraphics);
    paintSelection(g);
    paintChildren(g, EDITORS);
  }

  private Rectangle getBounds(Locator source) {
    Map<State, AndroidRootComponent> stateToComponent = getStateComponentAssociation().keyToValue;
    AndroidRootComponent component = stateToComponent.get(source.getState());
    return component.getBounds(getRenderedView(source));
  }

  @Override
  public void doLayout() {
    Map<Transition, Component> transitionToEditor = getTransitionEditorAssociation().keyToValue;

    for (Transition transition : myNavigationModel.getTransitions()) {
      Point sl = Utilities.centre(getBounds(transition.getSource()));
      Point dl = Utilities.centre(getBounds(transition.getDestination()));
      String gesture = transition.getType();
      if (gesture != null) {
        Component c = transitionToEditor.get(transition);
        c.setSize(c.getPreferredSize());
        int sx = (sl.x + dl.x - c.getWidth()) / 2;
        int sy = (sl.y + dl.y - c.getHeight()) / 2;
        c.setLocation(sx, sy);
      }
    }
  }

  /*
  private void addChildrenOld(Collection<State> states) {
    final Set<State> visited = new HashSet<State>();
    final Point location = new Point(GAP.width, GAP.height);
    final Point maxLocation = new Point(0, 0);
    final int gridWidth = PREVIEW_SIZE.width + GAP.width;
    final int gridHeight = PREVIEW_SIZE.height + GAP.height;
    getStateComponentAssociation().clear();
    for (State state : states) {
      if (visited.contains(state)) {
        continue;
      }
      new Object() {
        public void addChildrenFor(State source) {
          visited.add(source);
          add(createRootComponentFor(source, location));
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
  */

  private <K, V extends Component> void removeLeftovers(Assoc<K, V> assoc, Collection<K> a) {
    for (Map.Entry<K, V> e : new ArrayList<Map.Entry<K, V>>(assoc.keyToValue.entrySet())) {
      K k = e.getKey();
      V v = e.getValue();
      if (!a.contains(k)) {
        assoc.remove(k, v);
        remove(v);
        repaint();
      }
    }
  }

  private JComboBox createEditorFor(final Transition transition) {
    String gesture = transition.getType();
    JComboBox c = new JComboBox(new Object[]{"", "click", "list", "menu", "contains", "update"});
    c.setSelectedItem(gesture);
    c.setForeground(getForeground());
    //c.setBorder(LABEL_BORDER);
    //c.setOpaque(true);
    c.setBackground(BACKGROUND_COLOR);
    c.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        transition.setType((String)itemEvent.getItem());
        myNavigationModel.getListeners().notify(NavigationModel.Event.update(Transition.class));
      }
    });
    return c;
  }

  private void syncTransitionCache(Assoc<Transition, Component> assoc) {
    // add anything that is in the model but not in our cache
    for (Transition transition : myNavigationModel.getTransitions()) {
      if (!assoc.keyToValue.containsKey(transition)) {
        Component editor = createEditorFor(transition);
        add(editor);
        assoc.add(transition, editor);
      }
    }
    // remove anything that is in our cache but not in the model
    removeLeftovers(assoc, myNavigationModel.getTransitions());
  }

  @Nullable
  private static VirtualFile getFile(State state, VirtualFileSystem fileSystem, String path, String dir) {
    return fileSystem.findFileByPath(path + dir + state.getXmlResourceName() + ".xml");
  }

  private AndroidRootComponent createRootComponentFor(State state, Point location) {
    VirtualFileSystem fileSystem = myFile.getFileSystem();
    String path = myFile.getParent().getParent().getPath();
    String directoryName = myFile.getParent().getName();
    int index = directoryName.indexOf('-');
    String qualifier = index == -1 ? "" : directoryName.substring(index + 1);
    VirtualFile qualifiedFile = getFile(state, fileSystem, path, "/layout" + qualifier + "/");
    VirtualFile file = qualifiedFile != null ? qualifiedFile : getFile(state, fileSystem, path, "/layout/");
    PsiFile psiFile = file == null ? null : PsiManager.getInstance(myMyRenderingParams.myProject).findFile(file);
    AndroidRootComponent result = new AndroidRootComponent(myMyRenderingParams, psiFile);
    result.setScale(myScale);
    result.setLocation(location);
    Dimension size = getPreviewSize();
    result.setPreferredSize(size);
    result.setSize(size);
    return result;
  }

  private Dimension getDeviceScreenSize() {
    Configuration configuration = myMyRenderingParams.myConfiguration;
    Device device = configuration.getDevice();
    if (device == null) {
      return ZERO_SIZE;
    }
    com.android.sdklib.devices.State deviceState = configuration.getDeviceState();
    if (deviceState == null) {
      deviceState = device.getDefaultState();
    }
    return notNull(device.getScreenSize(deviceState.getOrientation()));
  }

  private Dimension getPreviewSize() {
    return scale(getDeviceScreenSize(), myScale);
  }

  private void setPreferredSize(Set<AndroidRootComponent> roots) {
    Dimension size = getPreviewSize();
    Dimension gridSize = new Dimension(size.width + GAP.width, size.height + GAP.height);
    Point maxLoc = new Point(0, 0);
    for (AndroidRootComponent c : roots) {
      maxLoc = Utilities.max(maxLoc, c.getLocation());
    }
    setPreferredSize(new Dimension(maxLoc.x + gridSize.width, maxLoc.y + gridSize.height));
  }

  private void syncStateCache(Assoc<State, AndroidRootComponent> assoc) {
    // add anything that is in the model but not in our cache
    for (State state : myNavigationModel.getStates()) {
      if (!assoc.keyToValue.containsKey(state)) {
        Point p = Utilities.toAWTPoint(state.getLocation());
        AndroidRootComponent root = createRootComponentFor(state, p);
        assoc.add(state, root);
        add(root);
      }
    }
    // remove anything that is in our cache but not in the model
    removeLeftovers(assoc, myNavigationModel.getStates());

    setPreferredSize(assoc.valueToKey.keySet());
  }

  private Selections.Selection createSelection(Point mouseDownLocation, boolean shiftDown) {
    Component component = getComponentAt(mouseDownLocation);
    if (component instanceof NavigationEditorPanel2) {
      return Selections.NULL;
    }
    Transition transition = getTransitionEditorAssociation().valueToKey.get(component);
    if (component instanceof AndroidRootComponent) {
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      if (!shiftDown) {
        return new Selections.AndroidRootComponentSelection(myNavigationModel, androidRootComponent, mouseDownLocation, transition,
                                                            getStateComponentAssociation().valueToKey.get(androidRootComponent));
      }
      else {
        RenderedView leaf = getRenderedView(androidRootComponent, mouseDownLocation);
        return new Selections.RelationSelection(myNavigationModel, androidRootComponent, mouseDownLocation, getNamedParent(leaf), this);
      }
    }
    else {
      return new Selections.ComponentSelection<Component>(myNavigationModel, component, transition);
    }
  }

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      Point location = e.getPoint();
      boolean modified = (e.isShiftDown() || e.isControlDown() || e.isMetaDown()) && !e.isPopupTrigger();
      setSelection(createSelection(location, modified));
      requestFocus();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      setMouseLocation(e.getPoint());
    }

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
    private int applicableDropCount = 0;

    private void dropOrPrepareToDrop(DnDEvent anEvent, boolean execute) {
      Object attachedObject = anEvent.getAttachedObject();
      if (attachedObject instanceof TransferableWrapper) {
        TransferableWrapper wrapper = (TransferableWrapper)attachedObject;
        PsiElement[] psiElements = wrapper.getPsiElements();
        Point dropLocation = diff(anEvent.getPointOn(NavigationEditorPanel2.this), point(scale(getPreviewSize(), 0.5f)));

        if (psiElements != null) {
          for (PsiElement element : psiElements) {
            if (element instanceof PsiQualifiedNamedElement) {
              PsiQualifiedNamedElement namedElement = (PsiQualifiedNamedElement)element;
              String qualifiedName = namedElement.getQualifiedName();
              if (qualifiedName != null) {
                State state = new State(qualifiedName);
                state.setLocation(Utilities.toNavPoint(snap(dropLocation, MIDDLE_SNAP_GRID)));
                String name = namedElement.getName();
                if (name != null) {
                  state.setXmlResourceName(getXmlFileNameFromJavaFileName(name));
                }
                if (!getStateComponentAssociation().keyToValue.containsKey(state)) {
                  if (execute) {
                    myNavigationModel.addState(state);
                  }
                  else {
                    applicableDropCount++;
                  }
                }
                dropLocation = Utilities.add(dropLocation, MULTIPLE_DROP_STRIDE);
              }
            }
          }
        }
      }
      if (execute) {
        revalidate();
        repaint();
      }
    }

    @Override
    public boolean update(DnDEvent anEvent) {
      applicableDropCount = 0;
      dropOrPrepareToDrop(anEvent, false);
      anEvent.setDropPossible(applicableDropCount > 0);
      return false;
    }

    @Override
    public void drop(DnDEvent anEvent) {
      dropOrPrepareToDrop(anEvent, true);
    }


    @Override
    public void cleanUpOnLeave() {
    }

    @Override
    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }

  }

  private static String getXmlFileNameFromJavaFileName(String name) {
    return Utilities.getXmlFileNameFromJavaFileName(name);
  }

}
