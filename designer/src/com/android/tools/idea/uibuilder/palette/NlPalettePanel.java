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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.dependencies.DependencyManager;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.designer.DesignerEditorPanelFacade;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPalettePanel extends JPanel implements LightToolWindowContent, ConfigurationListener, LafManagerListener, DataProvider {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);
  private static final double PREVIEW_SCALE = 0.5;
  private static final int ICON_SPACER = 4;

  @NonNull private final JTree myTree;
  @NonNull private final NlPaletteModel myModel;
  @NonNull private final IconPreviewFactory myIconFactory;
  @NonNull private final DesignerEditorPanelFacade myDesigner;
  @NonNull private final Set<String> myMissingLibraries;
  @NonNull private final Disposable myDisposable;
  @NonNull private Mode myMode;
  @Nullable private ScalableDesignSurface myDesignSurface;
  @Nullable private BufferedImage myLastDragImage;

  public NlPalettePanel(@NonNull DesignerEditorPanelFacade designer) {
    myDesigner = designer;
    myModel = NlPaletteModel.get();
    myTree = new DnDAwareTree();
    myIconFactory = IconPreviewFactory.get();
    myMissingLibraries = new HashSet<String>();
    myDisposable = Disposer.newDisposable();
    myMode = Mode.ICON_AND_TEXT;
    initTree();
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    setLayout(new BorderLayout());
    add(pane, BorderLayout.CENTER);
  }

  @NonNull
  public JComponent getFocusedComponent() {
    return myTree;
  }

  public enum Mode {
    ICON_AND_TEXT("Show Icon and Text"),
    PREVIEW("Show Preview");

    private final String myMenuText;

    Mode(String menuText) {
      myMenuText = menuText;
    }

    @NonNull
    public String getMenuText() {
      return myMenuText;
    }
  }

  public void setDesignSurface(@Nullable ScalableDesignSurface designSurface) {
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        configuration.removeListener(this);
      }
    }
    myDesignSurface = designSurface;
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        configuration.addListener(this);
      }
    }
    checkForNewMissingDependencies();
    setMode(myMode);
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

  @Override
  public void lookAndFeelChanged(LafManager source) {
    setColors();
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
    myTree.setBackground(background);
    myTree.setForeground(foreground);
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

  // ---- implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    setMode(myMode);
    return true;
  }

  @NonNull
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
    public void actionPerformed(@NonNull AnActionEvent e) {
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

  private void showOptionPopup(@NonNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TogglePaletteModeAction(this, Mode.ICON_AND_TEXT));
    group.add(new TogglePaletteModeAction(this, Mode.PREVIEW));

    ActionPopupMenu popupMenu = ((ActionManagerImpl)ActionManager.getInstance())
      .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, group, new MenuItemPresentationFactory(true));
    popupMenu.getComponent().show(component, x, y);
  }

  @NonNull
  public Mode getMode() {
    return myMode;
  }

  public void setMode(@NonNull Mode mode) {
    myMode = mode;
    if (mode == Mode.PREVIEW && myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        myIconFactory.load(configuration, new Runnable() {
          @Override
          public void run() {
            setColors();
            invalidateUI();
          }
        });
      }
    }
    else {
      setColors();
      invalidateUI();
    }
  }

  private void invalidateUI() {
    // BasicTreeUI keeps a cache of node heights. This will replace the ui and force a new node height computation.
    IJSwingUtilities.updateComponentTreeUI(myTree);
  }

  private void initTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree.setModel(treeModel);
    myTree.setRowHeight(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setBorder(new EmptyBorder(INSETS));
    myTree.setToggleClickCount(1);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    createCellRenderer(myTree);
    addData(myModel, rootNode);
    expandAll(myTree, rootNode);
    myTree.setSelectionRow(0);
    new PaletteSpeedSearch(myTree);
    enableDnD();
    updateColorsAfterColorThemeChange(true);
    enableClickToLoadMissingDependency();
  }


  private static void expandAll(@NonNull JTree tree, @NonNull DefaultMutableTreeNode rootNode) {
    TreePath rootPath = new TreePath(rootNode);
    tree.expandPath(rootPath);
    TreeNode child = rootNode.getLastChild();
    while (child != null) {
      tree.expandPath(rootPath.pathByAddingChild(child));
      child = rootNode.getChildBefore(child);
    }
  }

  private void createCellRenderer(@NonNull JTree tree) {
    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NonNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object content = node.getUserObject();
        if (content instanceof NlPaletteItem) {
          NlPaletteItem item = (NlPaletteItem)content;
          BufferedImage image = null;
          if (!needsLibraryLoad(item) && myMode == Mode.PREVIEW && myDesignSurface != null && myDesignSurface.getConfiguration() != null) {
            image = myIconFactory.getImage(item, myDesignSurface.getConfiguration(), PREVIEW_SCALE);
          }
          if (image != null) {
            setIcon(new ImageIcon(image));
          }
          else if (needsLibraryLoad(item)) {
            Icon icon = item.getIcon();
            if (icon == null) {
              icon = AndroidIcons.Views.View;
            }
            Icon download = AllIcons.Actions.Download;
            image = UIUtil
              .createImage(download.getIconWidth() + icon.getIconWidth() + ICON_SPACER, icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = (Graphics2D)image.getGraphics();
            icon.paintIcon(myTree, g2, 0, 0);
            download.paintIcon(myTree, g2, icon.getIconWidth() + ICON_SPACER, 0);
            g2.dispose();

            append(item.getTitle());
            setIcon(new ImageIcon(image));
          }
          else {
            append(item.getTitle());
            setIcon(item.getIcon());
          }
        }
        else if (content instanceof NlPaletteGroup) {
          NlPaletteGroup group = (NlPaletteGroup)content;
          append(group.getTitle());
          setIcon(AllIcons.Nodes.Folder);
        }
      }
    };
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 0, 0));
    tree.setCellRenderer(renderer);
  }

  private boolean needsLibraryLoad(@NonNull NlPaletteItem item) {
    return !Collections.disjoint(myMissingLibraries, item.getLibraries());
  }

  private static void addData(@NonNull NlPaletteModel model, @NonNull DefaultMutableTreeNode rootNode) {
    for (NlPaletteGroup group : model.getGroups()) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
      for (NlPaletteItem item : group.getItems()) {
        DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(item);
        groupNode.add(itemNode);
      }
      rootNode.add(groupNode);
    }
  }

  private void enableDnD() {
    final DnDManager dndManager = DnDManager.getInstance();
    dndManager.registerSource(new PaletteDnDSource(), myTree);
  }

  private void enableClickToLoadMissingDependency() {
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        NlPaletteItem item = getItemForPath(myTree.getPathForLocation(event.getX(), event.getY()));
        if (item != null && needsLibraryLoad(item)) {
          Module module = getModule();
          DependencyManager manager = module != null ? DependencyManager.getDependencyManager(module.getProject()) : null;
          if (manager != null) {    // todo: remove when DependencyManager has been implemented for other BUILD cases.
            manager.ensureLibraryIsIncluded(module, item.getLibraries(), null);
          }
        }
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect(myDisposable)
      .subscribe(ProjectEx.ProjectSaved.TOPIC, new ProjectEx.ProjectSaved() {
        @Override
        public void saved(@NotNull final Project project) {
          Module module = getModule();
          if (module != null && module.getProject().equals(project)) {
            if (checkForNewMissingDependencies()) {
              invalidateUI();
            }
          }
        }
      });
  }

  private boolean checkForNewMissingDependencies() {
    Module module = getModule();
    DependencyManager manager = module != null ? DependencyManager.getDependencyManager(module.getProject()) : null;
    List<String> missing = Collections.emptyList();
    if (manager != null) {
      missing = manager.findMissingDependencies(module, myModel.getLibrariesUsed());
    }
    if (missing.size() == myMissingLibraries.size() && myMissingLibraries.containsAll(missing)) {
      return false;
    }
    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  @Nullable
  private Module getModule() {
    Configuration configuration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @Nullable
  private static NlPaletteItem getItemForPath(@Nullable TreePath path) {
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object content = node.getUserObject();
    return content instanceof NlPaletteItem ? (NlPaletteItem)content : null;
  }

  @Override
  public void dispose() {
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      if (configuration != null) {
        configuration.removeListener(this);
      }
    }
    updateColorsAfterColorThemeChange(false);
    Disposer.dispose(myDisposable);
  }

  private class PaletteDnDSource implements DnDSource {

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      NlPaletteItem item = getItemForPath(myTree.getPathForLocation(dragOrigin.x, dragOrigin.y));
      return item != null && !needsLibraryLoad(item);
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      NlPaletteItem item = getItemForPath(path);
      assert item != null;
      Dimension size = null;
      if (myDesignSurface != null) {
        ScreenView screenView = myDesignSurface.getCurrentScreenView();
        BufferedImage image = screenView != null ? myIconFactory.renderDragImage(item, screenView, 1.0) : null;
        if (image != null) {
          size = new Dimension(image.getWidth(), image.getHeight());
          myLastDragImage = image;
        }
      }
      if (size == null) {
        Rectangle bounds = myTree.getPathBounds(path);
        size = bounds != null ? bounds.getSize() : new Dimension(200, 100);
        if (myDesignSurface != null) {
          double scale = myDesignSurface.getScale();
          size.setSize(size.getWidth() / scale, size.getHeight() / scale);
        }
      }
      if (myDesigner instanceof NlPreviewForm) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ((NlPreviewForm)myDesigner).minimizePalette();
          }
        });
      }
      DnDTransferComponent component = new DnDTransferComponent(item.getId(), item.getRepresentation(), size.width, size.height);
      return new DnDDragStartBean(new ItemTransferable(new DnDTransferItem(component)));
    }

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      BufferedImage image = null;
      if (myLastDragImage != null && myDesignSurface != null) {
        double scale = myDesignSurface.getScale();
        image = ImageUtils.scale(myLastDragImage, scale, scale);
        myLastDragImage = null;
      }
      if (image == null) {
        int row = myTree.getRowForPath(path);
        Component comp =
          myTree.getCellRenderer().getTreeCellRendererComponent(myTree, path.getLastPathComponent(), false, true, true, row, false);
        comp.setForeground(myTree.getForeground());
        comp.setBackground(myTree.getBackground());
        comp.setFont(myTree.getFont());
        comp.setSize(comp.getPreferredSize());
        image = UIUtil.createImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D)image.getGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        comp.paint(g2);
        g2.dispose();
      }
      return Pair.<Image, Point>pair(image, new Point(-image.getWidth() / 2, -image.getHeight() / 2));
    }

    @Override
    public void dragDropEnd() {
      myTree.clearSelection();
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static final class PaletteSpeedSearch extends TreeSpeedSearch {
    PaletteSpeedSearch(@NonNull JTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (pattern == null) {
        return false;
      }
      TreePath path = (TreePath)element;
      NlPaletteItem item = getItemForPath(path);
      return item != null && compare(item.getTitle(), pattern);
    }
  }

  private class ActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {

    @Override
    public void performCopy(@NonNull DataContext dataContext) {
      TreePath path = myTree.getSelectionPath();
      NlPaletteItem item = getItemForPath(path);
      if (item != null && !needsLibraryLoad(item)) {
        DnDTransferComponent component = new DnDTransferComponent(item.getId(), item.getRepresentation(), 0, 0);
        CopyPasteManager.getInstance().setContents(new ItemTransferable(new DnDTransferItem(component)));
      }
    }

    @Override
    public boolean isCopyEnabled(@NonNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isCopyVisible(@NonNull DataContext dataContext) {
      return true;
    }

    @Override
    public void performCut(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean isCutEnabled(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public boolean isCutVisible(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public void deleteElement(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean canDeleteElement(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public void performPaste(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean isPastePossible(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public boolean isPasteEnabled(@NonNull DataContext dataContext) {
      return false;
    }
  }
}
