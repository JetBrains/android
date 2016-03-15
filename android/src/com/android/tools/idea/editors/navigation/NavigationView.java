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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.CodeGenerator;
import com.android.tools.idea.editors.navigation.macros.FragmentEntry;
import com.android.tools.idea.editors.navigation.model.*;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.npw.NewAndroidActivityWizard;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

import static com.android.tools.idea.editors.navigation.NavigationEditorUtils.*;

public class NavigationView extends JComponent {
  private static final Logger LOG = Logger.getInstance(NavigationView.class.getName());
  public static final ModelDimension GAP = new ModelDimension(500, 100);
  private static final Color BACKGROUND_COLOR = new JBColor(Gray.get(192), Gray.get(70));
  private static final Color TRIGGER_BACKGROUND_COLOR = new JBColor(Gray.get(200), Gray.get(60));
  private static final Color SNAP_GRID_LINE_COLOR_MINOR = new JBColor(Gray.get(180), Gray.get(60));
  private static final Color SNAP_GRID_LINE_COLOR_MIDDLE = new JBColor(Gray.get(170), Gray.get(50));
  private static final Color SNAP_GRID_LINE_COLOR_MAJOR = new JBColor(Gray.get(160), Gray.get(40));

  public static final float ZOOM_FACTOR = 1.1f;

  // Snap grid
  private static final int MINOR_SNAP = JBUI.scale(32);
  private static final int MIDDLE_COUNT = JBUI.scale(5);
  private static final int MAJOR_COUNT = JBUI.scale(10);

  public static final Dimension MINOR_SNAP_GRID = new Dimension(MINOR_SNAP, MINOR_SNAP);
  public static final Dimension MIDDLE_SNAP_GRID = scale(MINOR_SNAP_GRID, MIDDLE_COUNT);
  public static final Dimension MAJOR_SNAP_GRID = scale(MINOR_SNAP_GRID, MAJOR_COUNT);
  public static final int MIN_GRID_LINE_SEPARATION = 8;

  public static final int LINE_WIDTH = 12;
  private static final Point MULTIPLE_DROP_STRIDE = point(MAJOR_SNAP_GRID);
  private static final Condition<Component> SCREENS = instanceOf(AndroidRootComponent.class);
  private static final Condition<Component> EDITORS = Conditions.not(SCREENS);
  private static final boolean DRAW_DESTINATION_RECTANGLES = false;
  private static final boolean DEBUG = false;
  // See http://www.google.com/design/spec/patterns/gestures.html#gestures-gestures
  private static final Color GESTURE_ICON_COLOR = new JBColor(new Color(0xE64BA7), new Color(0xE64BA7));
  private static final String DEVICE_DEFAULT_THEME_NAME = SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme.DeviceDefault";
  public static final int RESOURCE_SUFFIX_LENGTH = ".xml".length();
  public static final String LIST_VIEW_ID = "list";
  public static final int FAKE_OVERFLOW_MENU_WIDTH = 10;
  public static final boolean SHOW_FAKE_OVERFLOW_MENUS = true;

  private final RenderingParameters myRenderingParams;
  private final NavigationModel myNavigationModel;
  private final SelectionModel mySelectionModel;
  private final CodeGenerator myCodeGenerator;

  private final BiMap<State, AndroidRootComponent> myStateComponentAssociation = HashBiMap.create();
  private final BiMap<Transition, Component> myTransitionEditorAssociation = HashBiMap.create();

  private boolean myStateCacheIsValid;
  private boolean myTransitionEditorCacheIsValid;
  private Map<State, Map<String, RenderedView>> myNameToRenderedView = new IdentityHashMap<State, Map<String, RenderedView>>();
  private Image myBackgroundImage;
  private Point myMouseLocation;
  private Transform myTransform = new Transform(1 / 4f);

  // Configuration

  private boolean myShowRollover = false;
  @SuppressWarnings("FieldCanBeLocal") private boolean myDrawGrid = false;

  public NavigationView(RenderingParameters renderingParams,
                        NavigationModel model,
                        SelectionModel selectionModel,
                        CodeGenerator codeGenerator) {
    myRenderingParams = renderingParams;
    myNavigationModel = model;
    mySelectionModel = selectionModel;
    myCodeGenerator = codeGenerator;

    setFocusable(true);
    setLayout(null);

    // Mouse listener
    MouseAdapter mouseListener = new MyMouseListener();
    addMouseListener(mouseListener);
    addMouseMotionListener(mouseListener);

    // Popup menu
    final JPopupMenu menu = new JBPopupMenu();
    final JMenuItem anItem = new JBMenuItem("New Activity...");
    anItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        Module module = myRenderingParams.facet.getModule();
        NewAndroidActivityWizard dialog = new NewAndroidActivityWizard(module, null, null);
        dialog.init();
        dialog.setOpenCreatedFiles(false);
        dialog.show();
      }
    });
    menu.add(anItem);
    setComponentPopupMenu(menu);

    // Focus listener
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

    // Drag and Drop listener
    final DnDManager dndManager = DnDManager.getInstance();
    dndManager.registerTarget(new MyDnDTarget(), this);

    // Key listeners
    Action remove = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setSelection(Selections.NULL);
      }
    };
    registerKeyBinding(KeyEvent.VK_DELETE, "delete", remove);
    registerKeyBinding(KeyEvent.VK_BACK_SPACE, "backspace", remove);

    // Model listener
    myNavigationModel.getListeners().add(new Listener<Event>() {
      @Override
      public void notify(@NotNull Event event) {
        if (DEBUG) LOG.info("NavigationView:: <listener> " + myStateCacheIsValid + " " + myTransitionEditorCacheIsValid);
        if (event.operandType.isAssignableFrom(State.class)) {
          myStateCacheIsValid = false;
        }
        if (event.operandType.isAssignableFrom(Transition.class)) {
          myTransitionEditorCacheIsValid = false;
        }
        if (event == NavigationEditor.PROJECT_READ) {
          setSelection(Selections.NULL);
        }
        revalidate();
        repaint();
      }
    });
  }

  @Nullable
  private static RenderedView getRenderedView(AndroidRootComponent c, Point location) {
    return c.getRenderedView(diff(location, c.getLocation()));
  }

  @Nullable
  private String getFragmentClassName(State sourceState, @Nullable RenderedView namedSourceLeaf) {
    if (namedSourceLeaf == null) {
      return null;
    }
    if (sourceState instanceof ActivityState) {
      ActivityState sourceActivityState = (ActivityState)sourceState;
      XmlTag tag = namedSourceLeaf.tag;
      if (tag == null) {
        return null;
      }
      PsiFile fragmentFile = tag.getContainingFile();
      String resourceFileName = fragmentFile.getName();
      String resourceName = resourceFileName.substring(0, resourceFileName.length() - RESOURCE_SUFFIX_LENGTH);
      for (FragmentEntry fragment : sourceActivityState.getFragments()) {
        Module module = myRenderingParams.facet.getModule();
        String fragmentClassName = fragment.className;
        String resource = Analyser.getXMLFileName(module, fragmentClassName, false);
        if (resource == null) {
          PsiClass listClass = NavigationEditorUtils.getPsiClass(module, "android.app.ListFragment");
          if (listClass == null) {
            LOG.warn("Can't find: android.app.ListFragment");
            continue;
          }
          PsiClass psiClass = NavigationEditorUtils.getPsiClass(module, fragmentClassName);
          if (psiClass != null && (psiClass.isInheritor(listClass, true))) {
            if (tag.getName().equals("ListView")) {
              return fragmentClassName;
            }
          }
        }
        if (resourceName.equals(resource)) {
          return fragmentClassName;
        }
      }
    }
    return null;
  }

  void createTransition(AndroidRootComponent sourceComponent, @Nullable RenderedView namedSourceLeaf, Point mouseUpLocation) {
    Component destComponent = getComponentAt(mouseUpLocation);
    if (sourceComponent != destComponent) {
      if (destComponent instanceof AndroidRootComponent) {
        AndroidRootComponent destinationRoot = (AndroidRootComponent)destComponent;
        if (destinationRoot.isMenu()) {
          return;
        }
        RenderedView endLeaf = getRenderedView(destinationRoot, mouseUpLocation);
        RenderedView namedEndLeaf = HierarchyUtils.getNamedParent(endLeaf);

        Map<AndroidRootComponent, State> rootComponentToState = getStateComponentAssociation().inverse();
        State sourceState = rootComponentToState.get(sourceComponent);
        String fragmentClassName = getFragmentClassName(sourceState, namedSourceLeaf);
        Locator sourceLocator = Locator.of(sourceState, fragmentClassName, HierarchyUtils.getViewId(namedSourceLeaf));
        Locator destinationLocator = Locator.of(rootComponentToState.get(destComponent), HierarchyUtils.getViewId(namedEndLeaf));
        myCodeGenerator.implementTransition(new Transition(Transition.PRESS, sourceLocator, destinationLocator));
      }
    }
  }

  static Rectangle getBounds(AndroidRootComponent c, @Nullable RenderedView leaf) {
    if (leaf == null) {
      return c.getBounds();
    }
    Rectangle r = c.transform.getBounds(leaf);
    return new Rectangle(c.getX() + r.x + AndroidRootComponent.PADDING, c.getY() + r.y + AndroidRootComponent.getTopShift(), r.width, r.height);
  }

  Rectangle getNamedLeafBoundsAt(Component sourceComponent, Point location, boolean penetrate) {
    Component destComponent = getComponentAt(location);
    if (sourceComponent != destComponent) {
      if (destComponent instanceof AndroidRootComponent) {
        AndroidRootComponent destinationRoot = (AndroidRootComponent)destComponent;
        if (!destinationRoot.isMenu()) {
          if (!penetrate) {
            return destinationRoot.getBounds();
          }
          RenderedView endLeaf = getRenderedView(destinationRoot, location);
          RenderedView namedEndLeaf = HierarchyUtils.getNamedParent(endLeaf) ;
          return getBounds(destinationRoot, namedEndLeaf);
        }
      }
    }
    return new Rectangle(location);
  }

  public void setScale(float scale) {
    myTransform = new Transform(scale);
    myBackgroundImage = null;
    for (AndroidRootComponent root : getStateComponentAssociation().values()) {
      root.setScale(scale);
    }
    setPreferredSize();

    revalidate();
    repaint();
  }

  public void zoom(int n) {
    setScale(myTransform.myScale * (float)Math.pow(ZOOM_FACTOR, n));
  }

  private BiMap<State, AndroidRootComponent> getStateComponentAssociation() {
    if (!myStateCacheIsValid) {
      syncStateCache(myStateComponentAssociation);
      myStateCacheIsValid = true;
    }
    return myStateComponentAssociation;
  }

  private BiMap<Transition, Component> getTransitionEditorAssociation() {
    if (!myTransitionEditorCacheIsValid) {
      syncTransitionCache(myTransitionEditorAssociation);
      myTransitionEditorCacheIsValid = true;
    }
    return myTransitionEditorAssociation;
  }

  private static Map<String, RenderedView> computeNameToRenderedView(RenderedViewHierarchy hierarchy) {
    Map<String, RenderedView> result = new HashMap<String, RenderedView>();
    for (RenderedView root : hierarchy.getRoots()) {
      result.putAll(createViewNameToRenderedView(root));
    }
    return result;
  }

  private Map<String, RenderedView> getNameToRenderedView(State state) {
    Map<String, RenderedView> result = myNameToRenderedView.get(state);
    if (result == null) {
      AndroidRootComponent androidRootComponent = getStateComponentAssociation().get(state);
      if (androidRootComponent == null) {
        return Collections.emptyMap();
      }

      RenderResult renderResult = androidRootComponent.getRenderResult();
      if (renderResult == null) {
        return Collections.emptyMap(); // rendering library hasn't loaded, temporarily return an empty map
      }

      RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
      if (hierarchy == null) {
        return Collections.emptyMap();
      }

      myNameToRenderedView.put(state, result = computeNameToRenderedView(hierarchy));
    }
    return result;
  }

  private static void fillViewByIdMap(RenderedView parent, Map<String, RenderedView> map) {
    for (RenderedView child : parent.getChildren()) {
      String id = HierarchyUtils.getViewId(child);
      if (id != null) {
        map.put(id, child);
      }
      // The view of a ListActivity or ListFragment may not have an id.
      // To make th views of these special classes locatable, add an entry for all elements where the tag name is "ListView".
      // todo deal with multiple listViews in a single layout
      XmlTag tag = child.tag;
      if (tag != null) {
        if (tag.getName().equals("ListView")) {
          map.put(LIST_VIEW_ID, child);
        }
      }
      fillViewByIdMap(child, map);
    }
  }

  private static Map<String, RenderedView> createViewNameToRenderedView(@NotNull RenderedView root) {
    final Map<String, RenderedView> result = new HashMap<String, RenderedView>();
    // Add fake rendered view for overflow menus so that sources of a menu transitions are shown upper right
    if (SHOW_FAKE_OVERFLOW_MENUS) {
      int w = FAKE_OVERFLOW_MENU_WIDTH;
      result.put(Analyser.FAKE_OVERFLOW_MENU_ID, new RenderedView(root, null, null, root.x + root.w - w, 0, w, w));
    }
    fillViewByIdMap(root, result);
    return result;
  }

  static void paintLeaf(Graphics g, @Nullable RenderedView leaf, Color color, AndroidRootComponent component) {
    if (leaf != null) {
      Color oldColor = g.getColor();
      g.setColor(color);
      drawRectangle(g, getBounds(component, leaf));
      g.setColor(oldColor);
    }
  }

  private void registerKeyBinding(int keyCode, String name, Action action) {
    InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), name);
    getActionMap().put(name, action);
  }

  private void setSelection(@NotNull Selections.Selection selection) {
    mySelectionModel.setSelection(selection);
    // the re-validate() call shouldn't be necessary but removing it causes orphaned
    // combo-boxes to remain visible (and click-able) after a 'remove' operation
    revalidate();
    repaint();
  }

  private void moveSelection(Point location) {
    mySelectionModel.getSelection().moveTo(location);
    revalidate();
    repaint();
  }

  private void setMouseLocation(Point mouseLocation) {
    myMouseLocation = mouseLocation;
    if (myShowRollover) {
      repaint();
    }
  }

  private void finaliseSelectionLocation(Point location) {
    mySelectionModel.setSelection(mySelectionModel.getSelection().finaliseSelectionLocation(location));
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

  private void drawGrid(Graphics g, Color c, Dimension modelSize, int width, int height) {
    g.setColor(c);
    Dimension viewSize = myTransform.modelToView(ModelDimension.create(modelSize));
    if (viewSize.width < MIN_GRID_LINE_SEPARATION || viewSize.height < MIN_GRID_LINE_SEPARATION) {
      return;
    }
    for (int x = 0; x < myTransform.viewToModelW(width); x += modelSize.width) {
      int vx = myTransform.modelToViewX(x);
      g.drawLine(vx, 0, vx, getHeight());
    }
    for (int y = 0; y < myTransform.viewToModelH(height); y += modelSize.height) {
      int vy = myTransform.modelToViewY(y);
      g.drawLine(0, vy, getWidth(), vy);
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
    if (myDrawGrid) {
      g.drawImage(getBackGroundImage(), 0, 0, null);
    }
    else {
      Color tmp = getBackground();
      g.setColor(BACKGROUND_COLOR);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(tmp);
    }

    // draw component shadows
    for (Component c : getStateComponentAssociation().values()) {
      Rectangle r = c.getBounds();
      ShadowPainter.drawRectangleShadow(g, r.x, r.y, r.width - AndroidRootComponent.PADDING, r.height - AndroidRootComponent.PADDING);
    }
  }

  static Point[] getControlPoints(Rectangle src, Rectangle dst, Line midLine) {
    Point a = midLine.a;
    Point b = midLine.b;
    return new Point[]{project(a, src), a, b, project(b, dst)};
  }

  private Point[] getControlPoints(Transition t) {
    Rectangle srcBounds = getBounds(t.getSource());
    Rectangle dstBounds = getBounds(t.getDestination());
    return getControlPoints(srcBounds, dstBounds, NavigationEditorUtils.getMidLine(srcBounds, dstBounds));
  }

  private static int getTurnLength(Point[] points, float scale) {
    int N = points.length;
    int cornerDiameter = (int)(Math.min(MAJOR_SNAP_GRID.width, MAJOR_SNAP_GRID.height) * scale);

    for (int i = 0; i < N - 1; i++) {
      Point a = points[i];
      Point b = points[i + 1];

      int length = (int)length(diff(b, a));
      if (i != 0 && i != N - 2) {
        length /= 2;
      }
      cornerDiameter = Math.min(cornerDiameter, length);
    }
    return cornerDiameter;
  }

  private static void drawCurve(Graphics g, Point[] points, float scale) {
    final int N = points.length;
    final int cornerDiameter = getTurnLength(points, scale);

    boolean horizontal = points[0].x != points[1].x;
    Point previous = points[0];
    for (int i = 1; i < N - 1; i++) {
      Rectangle turn = getCorner(points[i], cornerDiameter);
      Point startTurn = project(previous, turn);
      drawLine(g, previous, startTurn);
      Point endTurn = project(points[i + 1], turn);
      drawCorner(g, startTurn, endTurn, horizontal);
      previous = endTurn;
      horizontal = !horizontal;
    }

    Point endPoint = points[N - 1];
    if (length(diff(previous, endPoint)) > 1) { //
      drawArrow(g, previous, endPoint, (int)(LINE_WIDTH * scale));
    }
  }

  public void drawTransition(Graphics g, Rectangle src, Rectangle dst, Point[] controlPoints) {
    // draw source rect
    drawRectangle(g, src);

    // draw curved 'Manhattan route' from source to destination
    drawCurve(g, controlPoints, myTransform.myScale);

    // draw destination rect
    if (DRAW_DESTINATION_RECTANGLES) {
      Color oldColor = g.getColor();
      g.setColor(JBColor.CYAN);
      drawRectangle(g, dst);
      g.setColor(oldColor);
    }
  }

  private void drawTransition(Graphics g, Transition t) {
    drawTransition(g, getBounds(t.getSource()), getBounds(t.getDestination()), getControlPoints(t));
  }

  public void paintTransitions(Graphics g) {
    for (Transition transition : myNavigationModel.getTransitions()) {
      drawTransition(g, transition);
    }
  }

  private static int angle(Point p) {
    //if ((p.x == 0) == (p.y == 0)) {
    //  throw new IllegalArgumentException();
    //}
    return p.x > 0 ? 0 : p.y < 0 ? 90 : p.x < 0 ? 180 : 270;
  }

  private static void drawCorner(Graphics g, Point a, Point b, boolean horizontal) {
    int radiusX = Math.abs(a.x - b.x);
    int radiusY = Math.abs(a.y - b.y);
    Point centre = horizontal ? new Point(a.x, b.y) : new Point(b.x, a.y);
    int startAngle = angle(diff(a, centre));
    int endAngle = angle(diff(b, centre));
    int dangle = endAngle - startAngle;
    int angle = dangle - (Math.abs(dangle) <= 180 ? 0 : 360 * sign(dangle));
    g.drawArc(centre.x - radiusX, centre.y - radiusY, radiusX * 2, radiusY * 2, startAngle, angle);
  }

  private RenderedView getRenderedView(Locator locator) {
    return getNameToRenderedView(locator.getState()).get(locator.getViewId());
  }

  private void paintRollover(Graphics2D lineGraphics) {
    if (myMouseLocation == null || !myShowRollover) {
      return;
    }
    Component component = getComponentAt(myMouseLocation);
    if (component instanceof AndroidRootComponent) {
      Stroke oldStroke = lineGraphics.getStroke();
      lineGraphics.setStroke(new BasicStroke(1));
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      RenderedView leaf = getRenderedView(androidRootComponent, myMouseLocation);
      RenderedView namedLeaf = HierarchyUtils.getNamedParent(leaf);
      paintLeaf(lineGraphics, leaf, JBColor.RED, androidRootComponent);
      paintLeaf(lineGraphics, namedLeaf, JBColor.BLUE, androidRootComponent);
      lineGraphics.setStroke(oldStroke);
    }
  }

  private void paintSelection(Graphics g) {
    mySelectionModel.getSelection().paint(g, hasFocus());
    mySelectionModel.getSelection().paintOver(g);
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
    Graphics2D lineGraphics = createLineGraphics(g, myTransform.modelToViewW(LINE_WIDTH));
    paintTransitions(lineGraphics);
    paintRollover(lineGraphics);
    paintSelection(g);
    paintChildren(g, EDITORS);
  }

  private Rectangle getBounds(Locator source) {
    Map<State, AndroidRootComponent> stateToComponent = getStateComponentAssociation();
    AndroidRootComponent component = stateToComponent.get(source.getState());
    return getBounds(component, getRenderedView(source));
  }

  @Override
  public void doLayout() {
    Map<Transition, Component> transitionToEditor = getTransitionEditorAssociation();

    Map<State, AndroidRootComponent> stateToComponent = getStateComponentAssociation();
    for (State state : stateToComponent.keySet()) {
      AndroidRootComponent root = stateToComponent.get(state);
      root.setLocation(myTransform.modelToView(myNavigationModel.getStateToLocation().get(state)));
      root.setSize(root.getPreferredSize());
    }

    for (Transition transition : myNavigationModel.getTransitions()) {
      String gesture = transition.getType();
      if (gesture != null) {
        Component editor = transitionToEditor.get(transition);
        if (editor == null) { // if model is changed on another thread we may see null here (with new notification system)
          continue;
        }
        if (editor.getParent() == null) { // unclear why this happens
          add(editor);
        }
        Dimension preferredSize = editor.getPreferredSize();
        Point[] points = getControlPoints(transition);
        Point location = diff(midPoint(points[1], points[2]), midPoint(preferredSize));
        editor.setLocation(location);
        editor.setSize(preferredSize);
      }
    }
  }

  private <K, V extends Component> void removeLeftovers(BiMap<K, V> assoc, Collection<K> a) {
    for (Map.Entry<K, V> e : new ArrayList<Map.Entry<K, V>>(assoc.entrySet())) {
      K k = e.getKey();
      V v = e.getValue();
      if (!a.contains(k)) {
        assoc.remove(k);
        remove(v);
        repaint();
      }
    }
  }

  private JComponent getPressGestureIcon() {
    return new JComponent() {
      private ModelDimension SIZE = new ModelDimension(100, 100);

      @Override
      public Dimension getPreferredSize() {
        return myTransform.modelToView(SIZE);
      }

      @Override
      public void paintComponent(Graphics g) {
        RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D)g).setRenderingHints(rh);
        g.setColor(GESTURE_ICON_COLOR);
        g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
      }
    };
  }

  private static JLabel getSwipeGestureIcon() {
    JLabel result = new JLabel("<->");
    result.setFont(result.getFont().deriveFont(JBUI.scale(20f)));
    result.setForeground(TRANSITION_LINE_COLOR);
    result.setBackground(TRIGGER_BACKGROUND_COLOR);
    result.setBorder(new LineBorder(TRANSITION_LINE_COLOR, 1));
    result.setOpaque(true);
    return result;
  }

  private Component createEditorFor(final Transition transition) {
    String gesture = transition.getType();
    return gesture.equals(Transition.PRESS) ? getPressGestureIcon() : getSwipeGestureIcon();
  }

  private void syncTransitionCache(BiMap<Transition, Component> assoc) {
    if (DEBUG) LOG.info("NavigationView: syncTransitionCache");
    // add anything that is in the model but not in our cache
    for (Transition transition : myNavigationModel.getTransitions()) {
      if (!assoc.containsKey(transition)) {
        Component editor = createEditorFor(transition);
        add(editor);
        assoc.put(transition, editor);
      }
    }
    // remove anything that is in our cache but not in the model
    removeLeftovers(assoc, myNavigationModel.getTransitions());
  }

  @Nullable
  private static VirtualFile getLayoutXmlVirtualFile(boolean menu, @Nullable String resourceName, Configuration configuration) {
    ResourceType resourceType = menu ? ResourceType.MENU : ResourceType.LAYOUT;
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    if (resourceResolver == null) {
      return null;
    }
    ResourceValue projectResource = resourceResolver.getProjectResource(resourceType, resourceName);
    if (projectResource == null) { /// seems to happen when we create a new resource
      return null;
    }
    return VfsUtil.findFileByIoFile(new File(projectResource.getValue()), false);
  }

  @Nullable
  public static PsiFile getLayoutXmlFile(boolean menu, @Nullable String resourceName, Configuration configuration, Project project) {
    VirtualFile file = getLayoutXmlVirtualFile(menu, resourceName, configuration);
    return file == null ? null : PsiManager.getInstance(project).findFile(file);
  }

  private RenderingParameters getActivityRenderingParameters(Module module, String className) {
    MergedManifest manifestInfo = ManifestInfo.get(module);
    Configuration newConfiguration = myRenderingParams.configuration.clone();
    String theme = manifestInfo.getManifestTheme();
    MergedManifest.ActivityAttributes activityAttributes = manifestInfo.getActivityAttributes(className);
    if (activityAttributes != null) {
      String activityTheme = activityAttributes.getTheme();
      theme = activityTheme != null ? activityTheme : theme;
    }
    newConfiguration.setTheme(theme);
    return myRenderingParams.withConfiguration(newConfiguration);
  }

  private AndroidRootComponent createUnscaledRootComponentFor(State state) {
    boolean isMenu = state instanceof MenuState;
    Module module = myRenderingParams.facet.getModule();
    String resourceName = Analyser.getXMLFileName(module, state.getClassName(), true);
    String menuName = isMenu ? ((MenuState) state).getXmlResourceName() : null;
    VirtualFile virtualFile = getLayoutXmlVirtualFile(false, resourceName, myRenderingParams.configuration);
    if (virtualFile == null) {
      return new AndroidRootComponent(state.getClassName(), myRenderingParams, null, menuName);
    }
    else {
      PsiFile psiFile = PsiManager.getInstance(myRenderingParams.project).findFile(virtualFile);
      RenderingParameters params = getActivityRenderingParameters(module, state.getClassName());
      return new AndroidRootComponent(state.getClassName(), params, psiFile, menuName);
    }
  }

  private AndroidRootComponent createRootComponentFor(State state) {
    AndroidRootComponent result = createUnscaledRootComponentFor(state);
    result.setScale(myTransform.myScale);
    return result;
  }

  private void syncStateCache(BiMap<State, AndroidRootComponent> assoc) {
    if (DEBUG) LOG.info("NavigationView: syncStateCache");
    assoc.clear();
    removeAll();
    //repaint();

    // add anything that is in the model but not in our cache
    for (State state : myNavigationModel.getStates()) {
      if (!assoc.containsKey(state)) {
        AndroidRootComponent root = createRootComponentFor(state);
        assoc.put(state, root);
        add(root);
      }
    }

    setPreferredSize();
  }

  private static ModelPoint getMaxLoc(Collection<ModelPoint> locations) {
    int maxX = 0;
    int maxY = 0;
    for (ModelPoint location : locations) {
      maxX = Math.max(maxX, location.x);
      maxY = Math.max(maxY, location.y);
    }
    return new ModelPoint(maxX, maxY);
  }

  private void setPreferredSize() {
    ModelDimension size = myRenderingParams.getDeviceScreenSize();
    ModelDimension gridSize = new ModelDimension(size.width + GAP.width, size.height + GAP.height);
    ModelPoint maxLoc = getMaxLoc(myNavigationModel.getStateToLocation().values());
    Dimension max = myTransform.modelToView(new ModelDimension(maxLoc.x + gridSize.width, maxLoc.y + gridSize.height));
    setPreferredSize(max);
  }

  private void bringToFront(@Nullable State state) {
    if (state != null) {
      AndroidRootComponent menuComponent = getStateComponentAssociation().get(state);
      if (menuComponent != null) {
        setComponentZOrder(menuComponent, 0);
      }
    }
  }

  private static void debug(@Nullable String s) {
    //if (DEBUG) System.out.println(s);
    //noinspection ConstantConditions
    LOG.debug(s);
  }

  private static void debug(String name, @Nullable RenderedView view) {
    if (DEBUG) debug(name + ": \n" + HierarchyUtils.toString(view));
  }

  private Selections.Selection createSelection(Point mouseDownLocation, boolean shiftDown) {
    Component component = getComponentAt(mouseDownLocation);
    if (component instanceof NavigationView) {
      return Selections.NULL;
    }
    Transition transition = getTransitionEditorAssociation().inverse().get(component);
    if (component instanceof AndroidRootComponent) {
      Point location = AndroidRootComponent.relativePoint(mouseDownLocation);
      // Select a top-level 'screen'
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      State state = getStateComponentAssociation().inverse().get(androidRootComponent);
      if (!shiftDown) {
        if (state == null) {
          return Selections.NULL;
        }
        bringToFront(state);
        if (state instanceof ActivityState) {
          bringToFront(myNavigationModel.findAssociatedMenuState((ActivityState)state));
        }
        return new Selections.AndroidRootComponentSelection(myNavigationModel, androidRootComponent, transition, myRenderingParams,
                                                            location, state, myTransform);
      }
      else {
        // Select a specific view
        RenderedView leaf = getRenderedView(androidRootComponent, location);
        if (leaf == null) {
          return Selections.NULL;
        }
        debug("root", HierarchyUtils.getRoot(leaf));
        debug("leaf", leaf);
        RenderedView namedParent = HierarchyUtils.getNamedParent(leaf);
        if (namedParent == null) {
          return Selections.NULL;
        }
        debug("namedParent", namedParent);

        if (myNavigationModel.findTransitionWithSource(Locator.of(state, HierarchyUtils.getViewId(namedParent))) != null) {
          return Selections.NULL;
        }
        return new Selections.ViewSelection(androidRootComponent, location, namedParent, this);
      }
    }
    else {
      // Select the transition/gesture component
      return new Selections.ComponentSelection<Component>(myRenderingParams, myNavigationModel, component, transition);
    }
  }

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }
      Point location = e.getPoint();
      boolean modified = (e.isShiftDown() || e.isControlDown() || e.isMetaDown());
      setSelection(createSelection(location, modified));
      requestFocus();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }
      setMouseLocation(e.getPoint());
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }
      moveSelection(e.getPoint());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        Component child = getComponentAt(e.getPoint());
        if (child instanceof AndroidRootComponent) {
          AndroidRootComponent androidRootComponent = (AndroidRootComponent)child;
          androidRootComponent.launchLayoutEditor();
        }
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }
      finaliseSelectionLocation(e.getPoint());
    }
  }

  private class MyDnDTarget implements DnDTarget {
    private int applicableDropCount = 0;

    private void execute(State state, boolean execute) {
      if (!getStateComponentAssociation().containsKey(state)) {
        if (execute) {
          myNavigationModel.addState(state);
        }
        else {
          applicableDropCount++;
        }
      }
    }

    private void dropOrPrepareToDrop(DnDEvent anEvent, boolean execute) {
      Object attachedObject = anEvent.getAttachedObject();
      if (attachedObject instanceof TransferableWrapper) {
        TransferableWrapper wrapper = (TransferableWrapper)attachedObject;
        PsiElement[] psiElements = wrapper.getPsiElements();
        Point dropLoc = anEvent.getPointOn(NavigationView.this);

        if (psiElements != null) {
          for (PsiElement element : psiElements) {
            if (element instanceof XmlFileImpl) {
              PsiFile containingFile = element.getContainingFile();
              PsiDirectory dir = containingFile.getParent();
              if (dir != null && dir.getName().equals(SdkConstants.FD_RES_MENU)) {
                String resourceName = ResourceHelper.getResourceName(containingFile);
                State state = new MenuState(resourceName);
                execute(state, execute);
              }
            }
            if (element instanceof PsiQualifiedNamedElement) {
              PsiQualifiedNamedElement namedElement = (PsiQualifiedNamedElement)element;
              String qualifiedName = namedElement.getQualifiedName();
              if (qualifiedName != null) {
                State state = new ActivityState(qualifiedName);
                Dimension size = myRenderingParams.getDeviceScreenSizeFor(myTransform);
                Point dropLocation = diff(dropLoc, midPoint(size));
                myNavigationModel.getStateToLocation().put(state, myTransform.viewToModel(snap(dropLocation, MIDDLE_SNAP_GRID)));
                execute(state, execute);
                dropLoc = NavigationEditorUtils.sum(dropLocation, MULTIPLE_DROP_STRIDE);
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
}
