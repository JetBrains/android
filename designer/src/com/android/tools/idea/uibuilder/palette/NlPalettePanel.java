/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.Reason;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.structure.NlComponentTree;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPalettePanel extends JPanel
  implements LightToolWindowContent, ConfigurationListener, ResourceChangeListener, LafManagerListener, DataProvider {

  private static final Insets INSETS = new Insets(0, 6, 0, 6);
  private static final int ICON_SPACER = 4;

  private final JTree myPaletteTree;
  private final IconPreviewFactory myIconFactory;
  private final NlPaletteModel myModel;
  private final Set<String> myMissingLibraries;
  private final Disposable myDisposable;
  private final DnDManager myDndManager;
  private final DnDSource myDndSource;

  private final NlComponentTree myStructureTree;

  private ScalableDesignSurface myDesignSurface;
  private Mode myMode;
  private BufferedImage myLastDragImage;
  private Configuration myConfiguration;

  public NlPalettePanel(@NotNull Project project, @NotNull DesignSurface designSurface) {
    myPaletteTree = new PaletteTree();
    myIconFactory = IconPreviewFactory.get();
    myModel = NlPaletteModel.get(project);
    myMissingLibraries = new HashSet<>();
    myDisposable = Disposer.newDisposable();
    myMode = Mode.ICON_AND_TEXT;

    myDndManager = DnDManager.getInstance();
    myDndSource = new PaletteDnDSource();
    myDndManager.registerSource(myDndSource, myPaletteTree);
    initTree(project);
    JScrollPane palettePane = ScrollPaneFactory.createScrollPane(myPaletteTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);

    myStructureTree = new NlComponentTree(designSurface);
    JComponent structurePane = createStructurePane(myStructureTree);

    Splitter splitter = new Splitter(true, 0.6f);
    splitter.setFirstComponent(palettePane);
    splitter.setSecondComponent(structurePane);

    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);
  }

  @NotNull
  private static JComponent createStructurePane(@NotNull NlComponentTree structureTree) {
    JPanel panel = new JPanel(new BorderLayout());
    JBLabel label = new JBLabel("Component Tree");
    panel.add(label, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(structureTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER));
    return panel;
  }

  @NotNull
  public JComponent getFocusedComponent() {
    return myPaletteTree;
  }

  public enum Mode {
    ICON_AND_TEXT("Show Icon and Text"),
    PREVIEW("Show Preview");

    private final String myMenuText;

    Mode(String menuText) {
      myMenuText = menuText;
    }

    @NotNull
    public String getMenuText() {
      return myMenuText;
    }
  }

  public void setDesignSurface(@Nullable ScalableDesignSurface designSurface) {
    Module prevModule = null;
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        prevModule = configuration.getModule();
        configuration.removeListener(this);
      }
    }
    Module newModule = null;
    myDesignSurface = designSurface;
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        newModule = configuration.getModule();
        configuration.addListener(this);
      }
      updateConfiguration();
    }
    if (prevModule != newModule) {
      if (prevModule != null) {
        AndroidFacet facet = AndroidFacet.getInstance(prevModule);
        if (facet != null) {
          ResourceNotificationManager manager = ResourceNotificationManager.getInstance(prevModule.getProject());
          manager.removeListener(this, facet, null, null);
        }
      }
      if (newModule != null) {
        AndroidFacet facet = AndroidFacet.getInstance(newModule);
        if (facet != null) {
          ResourceNotificationManager manager = ResourceNotificationManager.getInstance(newModule.getProject());
          manager.addListener(this, facet, null, null);
        }
        myIconFactory.dropCache();
      }
    }
    checkForNewMissingDependencies();
  }

  private void updateColorsAfterColorThemeChange(boolean doUpdate) {
    LafManager manager = LafManager.getInstance();
    if (doUpdate) {
      manager.addLafManagerListener(this);
    }
    else {
      manager.removeLafManagerListener(this);
    }
  }

  private void setColors() {
    Color background;
    Color foreground;
    Configuration configuration = null;
    if (myDesignSurface != null) {
      configuration = myDesignSurface.getConfiguration();
    }
    ResourceResolver resolver = null;
    if (configuration != null) {
      resolver = configuration.getResourceResolver();
    }
    if (resolver == null || myMode != Mode.PREVIEW) {
      foreground = UIUtil.getTreeForeground();
      background = UIUtil.getTreeBackground();
    }
    else {
      ResourceValue windowBackground = resolver.findItemInTheme("colorBackground", true);
      background = ResourceHelper.resolveColor(resolver, windowBackground, configuration.getModule().getProject());
      if (background == null) {
        background = UIUtil.getTreeBackground();
      }
      ResourceValue textForeground = resolver.findItemInTheme("colorForeground", true);
      foreground = ResourceHelper.resolveColor(resolver, textForeground, configuration.getModule().getProject());
      if (foreground == null) {
        foreground = UIUtil.getTreeForeground();
      }

      // Ensure the colors can be differentiated:
      if (Math.abs(ImageUtils.getBrightness(background.getRGB()) - ImageUtils.getBrightness(foreground.getRGB())) < 64) {
        if (ImageUtils.getBrightness(background.getRGB()) < 128) {
          foreground = JBColor.WHITE;
        }
        else {
          foreground = JBColor.BLACK;
        }
      }
    }
    myPaletteTree.setBackground(background);
    myPaletteTree.setForeground(foreground);
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return new ActionHandler();
    }
    return null;
  }

  @NotNull
  public AnAction[] getActions() {
    return new AnAction[]{new OptionAction()};
  }

  private class OptionAction extends AnAction {
    public OptionAction() {
      // todo: Find a set of different icons
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.ProjectConfigurable);
      presentation.setHoveredIcon(AllIcons.General.ProjectConfigurableBanner);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showOptionPopup(inputEvent.getComponent(), x, y);
    }
  }

  private void showOptionPopup(@NotNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TogglePaletteModeAction(this, Mode.ICON_AND_TEXT));
    group.add(new TogglePaletteModeAction(this, Mode.PREVIEW));

    ActionPopupMenu popupMenu = ((ActionManagerImpl)ActionManager.getInstance())
      .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, group, new MenuItemPresentationFactory(true));
    popupMenu.getComponent().show(component, x, y);
  }

  @NotNull
  public Mode getMode() {
    return myMode;
  }

  public void setMode(@NotNull Mode mode) {
    myMode = mode;
    setColors();
    invalidateUI();
  }

  private void updateConfiguration() {
    myConfiguration = null;
    if (myDesignSurface == null) {
      return;
    }
    Configuration designConfiguration = myDesignSurface.getConfiguration();
    if (designConfiguration == null) {
      return;
    }
    State designState = designConfiguration.getDeviceState();
    Configuration configuration = designConfiguration.clone();
    Device device = configuration.getDevice();
    if (device == null) {
      return;
    }

    // Override to a predefined density that closest matches the screens resolution
    Density override = null;
    int monitorResolution = Toolkit.getDefaultToolkit().getScreenResolution();
    for (Density density : Density.values()) {
      if (density.getDpiValue() > 0) {
        if (override == null || Math.abs(density.getDpiValue() - monitorResolution) < Math.abs(override.getDpiValue() - monitorResolution)) {
          override = density;
        }
      }
    }

    if (override != null) {
      device = new Device.Builder(device).build();
      for (State state : device.getAllStates()) {
        Screen screen = state.getHardware().getScreen();
        screen.setXDimension((int)(screen.getXDimension() * override.getDpiValue() / screen.getXdpi()));
        screen.setYDimension((int)(screen.getYDimension() * override.getDpiValue() / screen.getYdpi()));
        screen.setXdpi(override.getDpiValue());
        screen.setYdpi(override.getDpiValue());
        screen.setPixelDensity(override);
      }
      configuration.setDevice(device, false);
      if (designState != null) {
        configuration.setDeviceStateName(designState.getName());
      }
      myConfiguration = configuration;
    }
  }

  private void invalidateUI() {
    // BasicTreeUI keeps a cache of node heights. This will replace the ui and force a new node height computation.
    IJSwingUtilities.updateComponentTreeUI(myPaletteTree);
  }

  private void initTree(@NotNull Project project) {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myPaletteTree.setModel(treeModel);
    myPaletteTree.setRowHeight(0);
    myPaletteTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myPaletteTree.setRootVisible(false);
    myPaletteTree.setShowsRootHandles(false);
    myPaletteTree.setBorder(new EmptyBorder(INSETS));
    myPaletteTree.setToggleClickCount(2);
    ToolTipManager.sharedInstance().registerComponent(myPaletteTree);
    TreeUtil.installActions(myPaletteTree);
    createCellRenderer(myPaletteTree);
    myPaletteTree.setSelectionRow(0);
    new PaletteSpeedSearch(myPaletteTree);
    updateColorsAfterColorThemeChange(true);
    enableClickToLoadMissingDependency();
    DumbService.getInstance(project).smartInvokeLater(() -> {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myPaletteTree.getModel().getRoot();
      addItems(myModel.getPalette(myDesignSurface.getLayoutType()).getItems(), root);
      checkForNewMissingDependencies();
      expandAll(myPaletteTree, root);
    });
  }

  private static void expandAll(@NotNull JTree tree, @NotNull DefaultMutableTreeNode rootNode) {
    TreePath rootPath = new TreePath(rootNode);
    tree.expandPath(rootPath);
    TreeNode child = rootNode.getLastChild();
    while (child != null) {
      tree.expandPath(rootPath.pathByAddingChild(child));
      child = rootNode.getChildBefore(child);
    }
  }

  private void createCellRenderer(@NotNull JTree tree) {
    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object content = node.getUserObject();
        if (content instanceof Palette.Item) {
          Palette.Item item = (Palette.Item)content;
          BufferedImage image = null;
          if (!needsLibraryLoad(item) && myMode == Mode.PREVIEW && myConfiguration != null) {
            image = myIconFactory.getImage(item, myConfiguration, getScale(item));
          }
          if (image != null) {
            setIcon(new JBImageIcon(image));
            setToolTipText(item.getTitle());
          }
          else if (needsLibraryLoad(item)) {
            Icon icon = item.getIcon();
            Icon download = AllIcons.Actions.Download;
            int factor = SystemInfo.isAppleJvm ? 2 : 1;
            image = UIUtil.createImage(factor * (download.getIconWidth() + icon.getIconWidth() + ICON_SPACER),
                                       factor * icon.getIconHeight(),
                                       BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = (Graphics2D)image.getGraphics();
            g2.scale(factor, factor);
            icon.paintIcon(myTree, g2, 0, 0);
            download.paintIcon(myTree, g2, icon.getIconWidth() + ICON_SPACER, 0);
            g2.dispose();
            BufferedImage retina = ImageUtils.convertToRetina(image);
            if (retina != null) {
              image = retina;
            }

            append(item.getTitle());
            setIcon(new JBImageIcon(image));
          }
          else {
            append(item.getTitle());
            setIcon(item.getIcon());
          }
        }
        else if (content instanceof Palette.Group) {
          Palette.Group group = (Palette.Group)content;
          append(group.getName());
          setIcon(AllIcons.Nodes.Folder);
        }
      }
    };
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 0, 0));
    tree.setCellRenderer(renderer);
  }

  private static double getScale(@NotNull Palette.Item item) {
    double scale = item.getPreviewScale();
    if (scale <= 0.1 || scale > 5.0) {
      // Do not allow ridiculous custom scale factors.
      scale = 1.0;
    }
    return scale;
  }

  private boolean needsLibraryLoad(@Nullable Palette.BaseItem item) {
    if (!(item instanceof Palette.Item)) {
      return false;
    }
    Palette.Item paletteItem = (Palette.Item)item;
    return myMissingLibraries.contains(paletteItem.getGradleCoordinate());
  }

  private static void addItems(@NotNull List<Palette.BaseItem> items, @NotNull DefaultMutableTreeNode rootNode) {
    for (Palette.BaseItem item : items) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
      if (item instanceof Palette.Group) {
        Palette.Group group = (Palette.Group)item;
        addItems(group.getItems(), node);
      }
      rootNode.add(node);
    }
  }

  private void enableClickToLoadMissingDependency() {
    myPaletteTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        Palette.BaseItem object = getItemForPath(myPaletteTree.getPathForLocation(event.getX(), event.getY()));
        if (needsLibraryLoad(object)) {
          Palette.Item item = (Palette.Item)object;
          String coordinate = item.getGradleCoordinate();
          assert coordinate != null;
          Module module = getModule();
          assert module != null;
          GradleDependencyManager manager = GradleDependencyManager.getInstance(module.getProject());
          manager.ensureLibraryIsIncluded(module, toGradleCoordinates(Collections.singletonList(coordinate)), null);
        }
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(ProjectEx.ProjectSaved.TOPIC, project -> {
      Module module = getModule();
      if (module != null && module.getProject().equals(project)) {
        if (checkForNewMissingDependencies()) {
          repaint();
        }
      }
    });
  }

  private boolean checkForNewMissingDependencies() {
    Module module = getModule();
    List<String> missing = Collections.emptyList();
    if (module != null) {
      GradleDependencyManager manager = GradleDependencyManager.getInstance(module.getProject());
      missing = fromGradleCoordinates(
        manager.findMissingDependencies(module, myModel.getPalette(myDesignSurface.getLayoutType()).getGradleCoordinates()));
      if (missing.size() == myMissingLibraries.size() && myMissingLibraries.containsAll(missing)) {
        return false;
      }
    }
    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  @NotNull
  private static List<GradleCoordinate> toGradleCoordinates(@NotNull Collection<String> dependencies) {
    if (dependencies.isEmpty()) {
      return Collections.emptyList();
    }
    List<GradleCoordinate> coordinates = Lists.newArrayList();
    for (String dependency : dependencies) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency + ":+");
      if (coordinate == null) {
        continue;
      }
      coordinates.add(coordinate);
    }
    return coordinates;
  }

  @NotNull
  private static List<String> fromGradleCoordinates(@NotNull Collection<GradleCoordinate> coordinates) {
    if (coordinates.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> dependencies = Lists.newArrayList();
    for (GradleCoordinate coordinate : coordinates) {
      String dependency = coordinate.getId();
      if (dependency != null) {
        dependencies.add(dependency);
      }
    }
    return dependencies;
  }

  @Nullable
  private Module getModule() {
    Configuration configuration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @Nullable
  private static Palette.BaseItem getItemForPath(@Nullable TreePath path) {
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    return (Palette.BaseItem)node.getUserObject();
  }

  // ---- implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    updateConfiguration();
    repaint();
    return true;
  }

  // ---- implements ResourceChangeListener ----

  @Override
  public void resourcesChanged(@NotNull Set<Reason> reasons) {
    for (Reason reason : reasons) {
      // Drop the preview images created before this change.
      // Unless this is a configuration change since we cache images for each configuration.
      if (reason != Reason.CONFIGURATION_CHANGED) {
        myIconFactory.dropCache();
        return;
      }
    }
  }

  // ---- implements LafManagerListener ----

  @Override
  public void lookAndFeelChanged(LafManager source) {
    setColors();
  }

  // ---- implements LightToolWindowContent ----

  @Override
  public void dispose() {
    setDesignSurface(null);
    myDndManager.unregisterSource(myDndSource, myPaletteTree);
    ToolTipManager.sharedInstance().unregisterComponent(myPaletteTree);
    updateColorsAfterColorThemeChange(false);
    Disposer.dispose(myDisposable);
    myStructureTree.dispose();
  }

  // ---- inner classes ----

  /**
   * Tree with special handling for preview images.
   */
  private class PaletteTree extends DnDAwareTree {
    @Override
    public void paintComponent(Graphics g) {
      if (myMode == Mode.PREVIEW && myDesignSurface != null) {
        if (myConfiguration != null) {
          // We want to delay the generation of the preview images as much as possible because it is time consuming.
          // Do this just before the images are needed for painting.
          if (myIconFactory.load(myConfiguration, myModel.getPalette(myDesignSurface.getLayoutType()), false)) {
            // If we just generated the preview images, we must invalidate the row heights that the tree is
            // caching internally. Then invalidate and wait for the next paint.
            // Otherwise some images may be cropped.
            invalidateUI();
            return;
          }
        }
      }
      super.paintComponent(g);
    }
  }

  private class PaletteDnDSource implements DnDSource {

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      Palette.BaseItem content = getItemForPath(myPaletteTree.getPathForLocation(dragOrigin.x, dragOrigin.y));
      return content instanceof Palette.Item && !needsLibraryLoad(content);
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myPaletteTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      Palette.BaseItem content = getItemForPath(path);
      assert content instanceof Palette.Item;
      Palette.Item item = (Palette.Item)content;
      Dimension size = null;
      if (myDesignSurface != null) {
        ScreenView screenView = myDesignSurface.getCurrentScreenView();
        BufferedImage image = screenView != null ? myIconFactory.renderDragImage(item, screenView) : null;
        if (image != null) {
          size = new Dimension(image.getWidth(), image.getHeight());
          myLastDragImage = image;
        }
      }
      if (size == null) {
        Rectangle bounds = myPaletteTree.getPathBounds(path);
        size = bounds != null ? bounds.getSize() : new Dimension(200, 100);
        if (myDesignSurface != null) {
          double scale = myDesignSurface.getScale();
          size.setSize(size.getWidth() / scale, size.getHeight() / scale);
        }
      }
      myDesignSurface.minimizePaletteOnPreview();
      DnDTransferComponent component = new DnDTransferComponent(item.getTagName(), item.getXml(), size.width, size.height);
      return new DnDDragStartBean(new ItemTransferable(new DnDTransferItem(component)));
    }

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      TreePath path = myPaletteTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      BufferedImage image = null;
      if (myLastDragImage != null && myDesignSurface != null) {
        double scale = myDesignSurface.getScale();
        image = ImageUtils.scale(myLastDragImage, scale, scale);
        myLastDragImage = null;
      }
      if (image == null) {
        // We do not have a preview image to drag. Use the selected row as an image.
        int row = myPaletteTree.getRowForPath(path);
        Component comp =
          myPaletteTree
            .getCellRenderer().getTreeCellRendererComponent(myPaletteTree, path.getLastPathComponent(), false, true, true, row, false);
        comp.setForeground(myPaletteTree.getForeground());
        comp.setBackground(myPaletteTree.getBackground());
        comp.setFont(myPaletteTree.getFont());
        comp.setSize(comp.getPreferredSize());
        // Do not allocate the double size buffer here this will not be drawn as a retina image i.e. don't use UUtil.createImage()
        //noinspection UndesirableClassUsage
        image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D)image.getGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        comp.paint(g2);
        g2.dispose();
      }
      return Pair.pair(image, new Point(-image.getWidth() / 2, -image.getHeight() / 2));
    }

    @Override
    public void dragDropEnd() {
      myPaletteTree.clearSelection();
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static final class PaletteSpeedSearch extends TreeSpeedSearch {
    PaletteSpeedSearch(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (pattern == null) {
        return false;
      }
      TreePath path = (TreePath)element;
      Palette.BaseItem content = getItemForPath(path);
      return content instanceof Palette.Item && compare(((Palette.Item)content).getTitle(), pattern);
    }
  }

  private class ActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      TreePath path = myPaletteTree.getSelectionPath();
      Palette.BaseItem content = getItemForPath(path);
      if (content instanceof Palette.Item && !needsLibraryLoad(content)) {
        Palette.Item item = (Palette.Item)content;
        DnDTransferComponent component = new DnDTransferComponent(item.getTagName(), item.getXml(), 0, 0);
        CopyPasteManager.getInstance().setContents(new ItemTransferable(new DnDTransferItem(component)));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void performCut(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean isCutEnabled(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public boolean isCutVisible(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public void performPaste(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean isPastePossible(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public boolean isPasteEnabled(@NotNull DataContext dataContext) {
      return false;
    }
  }
}
