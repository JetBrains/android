/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.MaterialColorUtils;
import com.android.tools.idea.editors.theme.MaterialColors;
import com.android.tools.idea.editors.theme.StateListPicker;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.ui.SearchField;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidBaseLayoutRefactoringAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Resource Chooser, with previews. Based on ResourceDialog in the android-designer.
 * <P>
 * TODO:
 * <ul>
 *   <li> Finish color parsing</li>
 *   <li> Perform validation (such as cyclic layout resource detection for layout selection)</li>
 *   <li> Render drawables using layoutlib, e.g. drawable XML files, .9.png's, etc.</li>
 *   <li> Offer to create more resource types</li>
 * </ul>
 */
public class ChooseResourceDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(ChooseResourceDialog.class);

  private static final String TYPE_KEY = "ResourceType";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;
  public static final String APP_NAMESPACE_LABEL = "Project";

  @NotNull private final Module myModule;
  @Nullable private final XmlTag myTag;

  private final JComponent myContentPanel;
  private final JTabbedPane myTabbedPane;
  private final ResourcePanel[] myPanels;
  private final SearchTextField mySearchBox;
  private final JComponent myViewOption;
  private boolean isGridMode;

  // if we are picking a resource that can't be a color, then all these are null
  private @Nullable EditResourcePanel myColorPickerPanel;
  private @Nullable ColorPicker myColorPicker;

  // we can ONLY ever have the statelist picker in {@link ResourceType#COLOR} or {@link ResourceType#DRAWABLE} mode.
  // We only ever need one stateList picker because Android can never allow picking both types for any attribute.
  private @Nullable EditResourcePanel myStateListPickerPanel;
  private @Nullable StateListPicker myStateListPicker;

  private @Nullable ResourcePickerListener myResourcePickerListener;

  private boolean myAllowCreateResource = true;
  private final Action myNewResourceAction = new AbstractAction("New Resource", AllIcons.General.ComboArrowDown) {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComponent component = (JComponent)e.getSource();
      ActionPopupMenu popupMenu = createNewResourcePopupMenu();
      popupMenu.getComponent().show(component, 0, component.getHeight());
    }
  };
  private final AnAction myNewResourceValueAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceValue(type);
    }
  };
  private final AnAction myNewResourceFileAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceFile(type);
    }
  };
  private final AnAction myExtractStyleAction = new AnAction("Extract Style...") {
    @Override
    public void actionPerformed(AnActionEvent e) {
      extractStyle();
    }
  };

  private String myResultResourceName;

  private boolean myUseGlobalUndo;
  private RenderTask myRenderTask;

  public interface ResourcePickerListener {
    void resourceChanged(@Nullable String resource);
  }

  public ChooseResourceDialog(@NotNull Module module, @NotNull ResourceType[] types, @Nullable String value, @Nullable XmlTag tag) {
    this(module, types, value, false, tag, ResourceNameVisibility.SHOW, null);
  }

  public ChooseResourceDialog(@NotNull Module module,
                              @NotNull ResourceType[] types,
                              @NotNull String value,
                              boolean isFrameworkValue,
                              @NotNull ResourceNameVisibility resourceNameVisibility,
                              @Nullable String resourceNameSuggestion) {
    this(module, types, value, isFrameworkValue, null, resourceNameVisibility, resourceNameSuggestion);
  }

  private ChooseResourceDialog(@NotNull Module module,
                               @NotNull ResourceType[] types,
                               final @Nullable String value,
                               boolean isFrameworkValue,
                               @Nullable XmlTag tag,
                               @NotNull ResourceNameVisibility resourceNameVisibility,
                               @Nullable String resourceNameSuggestion) {
    super(module.getProject());
    myModule = module;
    myTag = tag;
    if (resourceNameSuggestion != null && resourceNameSuggestion.startsWith("#")) {
      throw new IllegalArgumentException("invalid name suggestion " + resourceNameSuggestion);
    }
    setTitle("Resources");

    if (ArrayUtil.contains(ResourceType.COLOR, types) || ArrayUtil.contains(ResourceType.DRAWABLE, types)) {

      Configuration configuration = ThemeEditorUtils.getConfigurationForModule(myModule);
      final ResourceResolver resolver = configuration.getResourceResolver();
      assert resolver != null;

      ResourceValue resValue = null;
      if (value != null) {
        resValue = resolver.findResValue(value, isFrameworkValue);
      }

      final ResourceType stateListType;
      final ResourceFolderType stateListFolderType;
      if (ArrayUtil.contains(ResourceType.DRAWABLE, types)) {
        stateListType = ResourceType.DRAWABLE;
        stateListFolderType = ResourceFolderType.DRAWABLE;
      }
      else {
        stateListType = ResourceType.COLOR;
        stateListFolderType = ResourceFolderType.COLOR;
      }

      ResourceHelper.StateList stateList = null;
      if (resValue != null) {
        stateList = ResourceHelper.resolveStateList(resolver, resValue, myModule.getProject());
        if (stateList != null && stateList.getType() != stateListType) {
          // this is very strange, this means we have asked to open the resource picker to allow drawables but with a color statelist
          // or to 'not allow drawables', but with a drawables statelist, must be a user error, this should not normally happen.
          LOG.warn("StateList type mismatch " + stateList.getType() + " " + stateListType);
          stateList = null;
        }
      }

      myStateListPicker = new StateListPicker(stateList, myModule, configuration);
      myStateListPickerPanel = new EditResourcePanel(myStateListPicker) {
        @Override
        @Nullable
        public ValidationInfo doValidate() {
          ValidationInfo error = super.doValidate();
          if (error == null) {
            error = myStateListPicker.getFrameworkResourceError();
          }
          if (error == null) {
            assert myStateListPickerPanel != null;
            int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(myStateListPickerPanel.getLocationSettings().getDirNames(), myModule);
            error = myStateListPicker.getApiError(minDirectoriesApi);
          }
          return error;
        }
      };
      myStateListPickerPanel.addResourceDialogSouthPanel(ResourceNameVisibility.FORCE, resourceNameSuggestion, true, stateListFolderType, false, stateListType);

      Color color = null;
      if (resValue != null) {
        color = ResourceHelper.resolveColor(resolver, resValue, myModule.getProject());
      }

      if (color == null) {
        color = ResourceHelper.parseColor(value);
      }
      myColorPicker = new ColorPicker(myDisposable, color, true, new ColorPickerListener() {
        @Override
        public void colorChanged(Color color) {
          notifyResourcePickerListeners(ResourceHelper.colorToString(color));
        }

        @Override
        public void closed(@Nullable Color color) {
        }
      });
      myColorPicker.pickARGB();

      myColorPickerPanel = new EditResourcePanel(myColorPicker);
      if (resourceNameVisibility != ResourceNameVisibility.HIDE) {
        myColorPickerPanel.addResourceDialogSouthPanel(resourceNameVisibility, resourceNameSuggestion, false, ResourceFolderType.VALUES, true, ResourceType.COLOR);
      }
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;

    if (ArrayUtil.contains(ResourceType.DRAWABLE, types) && !ArrayUtil.contains(ResourceType.COLOR, types)) {
      myPanels = new ResourcePanel[types.length + 1];
      myPanels[types.length] = new ResourcePanel(facet, ResourceType.COLOR, false);
    }
    else {
      myPanels = new ResourcePanel[types.length];
    }

    for (int i = 0; i < types.length; i++) {
      myPanels[i] = new ResourcePanel(facet, types[i], true);
    }

    final ToggleAction listView = new ToggleAction(null, "list", AndroidIcons.Views.ListView) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !isGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(!state);
      }
    };

    final ToggleAction gridView = new ToggleAction(null, "grid", AndroidIcons.Views.GridView) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return isGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(state);
      }
    };

    mySearchBox = new SearchField(true);
    mySearchBox.setMaximumSize(new Dimension(JBUI.scale(300), mySearchBox.getMaximumSize().height));
    mySearchBox.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        Condition condition = new Condition() {
          private String text = mySearchBox.getText();
          @Override
          public boolean value(Object o) {
            return StringUtil.containsIgnoreCase(o.toString(), text);
          }
        };
        for (ResourcePanel panel : myPanels) {
          panel.myList.setFilter(condition);
        }
      }
    });

    myViewOption = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(listView, gridView), true).getComponent();
    myViewOption.setBorder(null);
    myViewOption.setMaximumSize(new Dimension(JBUI.scale(100), myViewOption.getMaximumSize().height));

    //noinspection UndesirableClassUsage We install our own special UI, intellij stuff will break it
    myTabbedPane = new JTabbedPane(SwingConstants.LEFT);
    myTabbedPane.setUI(new SimpleTabUI());
    for (ResourcePanel panel : myPanels) {
      myTabbedPane.addTab(panel.getType().getDisplayName(), panel.myComponent);
      panel.expandAll();
    }

    // "@color/black" or "@android:color/black"
    if (value != null && value.startsWith("@")) {
      org.jetbrains.android.dom.resources.ResourceValue resourceValue = org.jetbrains.android.dom.resources.ResourceValue.reference(value);
      assert resourceValue != null;
      String name = resourceValue.getResourceName();
      assert name != null; // as we used ResourceValue.reference to create this object, name is never null
      String namespace = resourceValue.getNamespace();
      ResourceType type = resourceValue.getType();

      ResourcePanel panel = null;
      for (ResourcePanel aPanel : myPanels) {
        if (aPanel.getType().equals(type)) {
          panel = aPanel;
          break;
        }
      }
      // panel is null if the reference is incorrect, e.g. "@sdfgsdfgs" (user error).
      if (panel != null) {
        myTabbedPane.setSelectedComponent(panel.myComponent);
        panel.select(namespace, name);
      }
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        setupViewOptions();
      }
    });

    JComponent toolbar = Box.createHorizontalBox();
    toolbar.add(mySearchBox);
    toolbar.add(Box.createHorizontalStrut(JBUI.scale(20)));
    toolbar.add(myViewOption);
    toolbar.setBorder(new CompoundBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 1, 0), JBUI.Borders.empty(8)));

    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(myTabbedPane);
    myContentPanel.add(toolbar, BorderLayout.NORTH);

    setupViewOptions();
    init();
    // we need to trigger this once before the window is made visible to update any extra labels
    doValidate();
  }

  private void setupViewOptions() {
    myViewOption.setVisible(getSelectedPanel().supportsGridMode());
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    // will draw the line between the main panel and the action buttons.
    return DialogStyle.COMPACT;
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription,
                                    boolean isBackground,
                                    boolean displayWarning) {
    if (myColorPicker != null) {
      myColorPicker.setContrastParameters(contrastColorsWithDescription, isBackground, displayWarning);
    }
    if (myStateListPicker != null) {
      myStateListPicker.setContrastParameters(contrastColorsWithDescription, isBackground);
    }
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  public enum ResourceNameVisibility {
    /**
     * Don't show field with resource name at all.
     */
    HIDE,

    /**
     * Show field, but do not force name to be used.
     */
    SHOW,

    /**
     * Force creation of named color.
     */
    FORCE
  }



  @NotNull
  private ResourcePanel getSelectedPanel() {
    Component selectedComponent = myTabbedPane.getSelectedComponent();
    for (ResourcePanel panel : myPanels) {
      if (panel.myComponent == selectedComponent) {
        return panel;
      }
    }
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return getSelectedPanel().doValidate();
  }

  public void setResourcePickerListener(@Nullable ResourcePickerListener resourcePickerListener) {
    myResourcePickerListener = resourcePickerListener;
  }

  protected void notifyResourcePickerListeners(@Nullable String resource) {
    if (myResourcePickerListener != null) {
      myResourcePickerListener.resourceChanged(resource);
    }
  }

  public void generateColorSuggestions(@NotNull Color primaryColor, @NotNull String attributeName) {
    List<Color> suggestedColors = null;
    if (MaterialColors.PRIMARY_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestPrimaryColors();
    }
    else if (MaterialColors.PRIMARY_DARK_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestPrimaryDarkColors(primaryColor);
    }
    else if (MaterialColors.ACCENT_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestAccentColors(primaryColor);
    }
    if (suggestedColors != null) {
      assert myColorPicker != null;
      myColorPicker.setRecommendedColors(suggestedColors);
    }
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourcePanel panel = getSelectedPanel();
    ResourceType resourceType = panel.getType();

    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceType)) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + resourceType + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceType);
      actionGroup.add(myNewResourceFileAction);
    }
    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceType)) {
      String title = "New " + resourceType + " Value...";
      if (resourceType == ResourceType.LAYOUT) {
        title = "New Layout Alias";
      }
      myNewResourceValueAction.getTemplatePresentation().setText(title);
      myNewResourceValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceType);
      actionGroup.add(myNewResourceValueAction);
    }
    if (myTag != null && ResourceType.STYLE.equals(resourceType)) {
      final boolean enabled = AndroidBaseLayoutRefactoringAction.getLayoutViewElement(myTag) != null &&
                              AndroidExtractStyleAction.doIsEnabled(myTag);
      myExtractStyleAction.getTemplatePresentation().setEnabled(enabled);
      actionGroup.add(myExtractStyleAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    if (resourceType == ResourceType.COLOR) {
      ResourcePanel panel = getSelectedPanel();
      panel.showNewResource(ResourcePanel.COLOR);
      return;
    }

    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    if (!dialog.showAndGet()) {
      return;
    }

    Module moduleToPlaceResource = dialog.getModule();
    if (moduleToPlaceResource == null) {
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(moduleToPlaceResource, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceType resourceType) {
    // if we are not showing the stateList picker, and we do have a stateList in it, then we can open it to allow the user to edit it.
    if (myStateListPickerPanel != null && resourceType == myStateListPickerPanel.getLocationSettings().getType() && !myStateListPickerPanel.isShowing() &&
        myStateListPicker != null && myStateListPicker.getStateList() != null) {
      ResourcePanel panel = getSelectedPanel();
      panel.showNewResource(ResourcePanel.STATELIST);
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    XmlFile newFile = CreateResourceFileAction.createFileResource(facet, resourceType, null, null, null, true, null);

    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + resourceType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    assert myTag != null;
    final String resName = AndroidExtractStyleAction.doExtractStyle(myModule, myTag, false, null);
    if (resName == null) {
      return;
    }
    myResultResourceName = "@style/" + resName;
    close(OK_EXIT_CODE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    // TODO.should select the first list?
    return myPanels[0].myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return myAllowCreateResource ? new Action[]{myNewResourceAction} : new Action[0];
  }

  public ChooseResourceDialog setAllowCreateResource(boolean allowCreateResource) {
    myAllowCreateResource = allowCreateResource;
    return this;
  }

  public boolean getAllowCreateResource() {
    return myAllowCreateResource;
  }

  /**
   * Expands the location settings panel
   */
  public void openLocationSettings() {
    if (myColorPickerPanel != null) {
      myColorPickerPanel.openLocationSettings();
    }
    if (myStateListPickerPanel != null) {
      myStateListPickerPanel.openLocationSettings();
    }
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    myResultResourceName = getSelectedPanel().getValueForLivePreview();

    // we are about to close, and potentially create/edit resources, that may cause all sorts of refreshes, so lets clear any live preview values.
    notifyResourcePickerListeners(null);

    if (myColorPickerPanel != null && myColorPickerPanel.isShowing()) {
      if (myColorPickerPanel.myResourceNameVisibility == ResourceNameVisibility.FORCE ||
          (myColorPickerPanel.myResourceNameVisibility == ResourceNameVisibility.SHOW && !myColorPickerPanel.getResourceNameField().getText().isEmpty())) {
        String colorName = myColorPickerPanel.getResourceNameField().getText();
        Module module = myColorPickerPanel.getLocationSettings().getModule();
        String fileName = myColorPickerPanel.getLocationSettings().getFileName();
        List<String> dirNames = myColorPickerPanel.getLocationSettings().getDirNames();
        assert module != null;
        AndroidFacet facet = AndroidFacet.getInstance(module);
        assert facet != null;

        if (!AndroidResourceUtil.changeColorResource(facet, colorName, myResultResourceName, fileName, dirNames, myUseGlobalUndo)) {
          // Changing color resource has failed, one possible reason is that color isn't defined in the project.
          // Trying to create the color instead.
          AndroidResourceUtil.createValueResource(module, colorName, ResourceType.COLOR, fileName, dirNames, myResultResourceName);
        }

        myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + colorName;
      }
      // else we use the value we got at the start of the method
    }
    else if (myStateListPickerPanel != null && myStateListPickerPanel.isShowing()) {
      String stateListName = myStateListPickerPanel.getResourceNameField().getText();
      Module module = myStateListPickerPanel.getLocationSettings().getModule();
      List<String> dirNames = myStateListPickerPanel.getLocationSettings().getDirNames();
      ResourceFolderType resourceFolderType = ResourceFolderType.getFolderType(dirNames.get(0));
      ResourceType resourceType = ResourceType.getEnum(resourceFolderType.getName());

      List<VirtualFile> files = null;
      if (resourceType != null) {
        assert module != null;
        files = AndroidResourceUtil.findOrCreateStateListFiles(module, resourceFolderType, resourceType, stateListName, dirNames);
      }
      if (files != null) {
        assert myStateListPicker != null;
        ResourceHelper.StateList stateList = myStateListPicker.getStateList();
        assert stateList != null;
        AndroidResourceUtil.updateStateList(module, stateList, files);
      }

      if (resourceFolderType == ResourceFolderType.COLOR) {
        myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + stateListName;
      }
      else if (resourceFolderType == ResourceFolderType.DRAWABLE) {
        myResultResourceName = SdkConstants.DRAWABLE_PREFIX + stateListName;
      }
    }
    super.doOKAction();
  }

  void setGridMode(boolean gridMode) {
    isGridMode = gridMode;
    for (ResourcePanel panel : myPanels) {
      if (panel.supportsGridMode()) {
        panel.setGridMode(isGridMode);
      }
    }
  }

  public void setUseGlobalUndo(boolean useGlobalUndo) {
    myUseGlobalUndo = useGlobalUndo;
  }

  @NotNull
  private Icon getIcon(@NotNull ResourceItem item, int size) {
    Icon icon = item.getIcon();
    if (icon != null && size == icon.getIconWidth()) {
      return icon;
    }

    VirtualFile file = item.getFile();
    ResourceGroup group = item.getGroup();

    if (file != null && ImageFileTypeManager.getInstance().isImage(file)) {
      icon = new SizedIcon(size, new ImageIcon(file.getPath()));
    }
    else if (group.getType() == ResourceType.DRAWABLE || group.getType() == ResourceType.MIPMAP) {
      if (myRenderTask == null) {
        myRenderTask = DrawableRendererEditor.configureRenderTask(myModule, ThemeEditorUtils.getConfigurationForModule(myModule));
        myRenderTask.setMaxRenderSize(150, 150); // dont make huge images here
      }

      // TODO can we just use ResourceUrl here instead?
      BufferedImage image = myRenderTask.renderDrawable(item.getResourceValue());
      if (image != null) {
        icon = new SizedIcon(size, image);
      }
      // TODO maybe have a different icon for state list drawable
    }
    else if (group.getType() == ResourceType.COLOR) {
      Configuration config = ThemeEditorUtils.getConfigurationForModule(myModule);
      ResourceResolver resolver = config.getResourceResolver();
      assert resolver != null;
      Color color = ResourceHelper.resolveColor(resolver, item.getResourceValue(), myModule.getProject());
      if (color != null) { // maybe null for invalid color
        icon = new ColorIcon(size, color);
      }
      // TODO maybe have a different icon when the resource points to more then 1 color
    }

    if (icon == null) {
      // TODO, for resources with no icon, when we use RESOURCE_ITEM_ICON, we should not redo the lookup each time.
      icon = file == null || file.getFileType().getIcon() == null ? RESOURCE_ITEM_ICON : file.getFileType().getIcon();
    }
    item.setIcon(icon);
    return icon;
  }

  private static String getResourceElementValue(ResourceElement element) {
    String text = element.getRawText();
    if (StringUtil.isEmpty(text)) {
      return element.getXmlTag().getText();
    }
    return text;
  }

  private class ResourcePanel {

    private static final String NONE = "None";
    private static final String TEXT = "Text";
    private static final String COMBO = "Combo";
    private static final String COLOR = "Color";
    private static final String STATELIST = "Statelist";

    /**
     * list of namespaces that we can get resources from, null means the application,
     */
    private final String[] NAMESPACES = {null, SdkConstants.ANDROID_NS_NAME};

    public final @NotNull JBSplitter myComponent;
    private final @NotNull TreeGrid myList;
    private final @NotNull JPanel myPreviewPanel;
    private final @NotNull JTextPane myHtmlTextArea;
    private final JTextArea myComboTextArea;
    private final JComboBox myComboBox;
    private final @NotNull JLabel myNoPreviewComponent;

    private final @NotNull ResourceGroup[] myGroups;
    private final @NotNull ResourceType myType;

    public ResourcePanel(@NotNull AndroidFacet facet, @NotNull ResourceType type, boolean includeFileResources) {
      myType = type;

      myGroups = new ResourceGroup[NAMESPACES.length];
      for (int c = 0; c < NAMESPACES.length; c++) {
        ResourceManager manager = facet.getResourceManager(NAMESPACES[c]);
        assert manager != null;
        myGroups[c] = new ResourceGroup(NAMESPACES[c], type, manager, includeFileResources);
      }

      AbstractTreeStructure treeContentProvider = new TreeContentProvider(myGroups);

      myComponent = new JBSplitter(false, 0.5f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myList = new TreeGrid(treeContentProvider);

      myList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          showPreview(getSelectedElement(), true);
          notifyResourcePickerListeners(getValueForLivePreview());
        }
      });
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          ResourceItem selected = getSelectedElement();
          if (selected != null) {
            myResultResourceName = selected.getResourceUrl();
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myList);

      JScrollPane firstComponent = ScrollPaneFactory.createScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      firstComponent.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
      firstComponent.setBorder(null);
      firstComponent.setPreferredSize(JBUI.size(200,600));

      myComponent.setFirstComponent(firstComponent);

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myHtmlTextArea = new JTextPane();
      myHtmlTextArea.setEditable(false);
      myHtmlTextArea.setContentType(UIUtil.HTML_MIME);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myHtmlTextArea, true), TEXT);
      myHtmlTextArea.setPreferredSize(JBUI.size(400, 400));

      myComboTextArea = new JTextArea(5, 20);
      myComboTextArea.setEditable(false);
      myComboBox = new JComboBox();
      myComboBox.setMaximumRowCount(15);
      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          List<ResourceElement> resources = (List<ResourceElement>)myComboBox.getClientProperty(COMBO);
          myComboTextArea.setText(getResourceElementValue(resources.get(myComboBox.getSelectedIndex())));
        }
      });
      JPanel comboPanel = new JPanel(new BorderLayout(0, 1) {
        @Override
        public void layoutContainer(Container target) {
          super.layoutContainer(target);
          Rectangle bounds = myComboBox.getBounds();
          Dimension size = myComboBox.getPreferredSize();
          size.width += 20;
          myComboBox.setBounds((int)bounds.getMaxX() - size.width, bounds.y, size.width, size.height);
        }
      });
      comboPanel.add(ScrollPaneFactory.createScrollPane(myComboTextArea), BorderLayout.CENTER);
      comboPanel.add(myComboBox, BorderLayout.SOUTH);
      myPreviewPanel.add(comboPanel, COMBO);

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);

      // setup default list look and feel
      setGridMode(isGridMode);

      if (myType == ResourceType.COLOR) {
        assert myColorPickerPanel != null;
        myPreviewPanel.add(myColorPickerPanel, COLOR);
      }

      if (myStateListPickerPanel != null && myType == myStateListPickerPanel.getLocationSettings().getType()) {
        myPreviewPanel.add(myStateListPickerPanel, STATELIST);
      }

      showPreview(null, true);
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    public void showPreview(@Nullable ResourceItem element, boolean allowEditor) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      // TODO maybe have a element of "new Color" and "new StateList"

      if (allowEditor) {
        if ((myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE) && element != null) {
          ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myModule, true);
          assert repository != null;
          boolean inProject = repository.hasResourceItem(element.getGroup().getType(), element.getName());
          if (inProject) {
            List<com.android.ide.common.res2.ResourceItem> items = repository.getResourceItem(element.getGroup().getType(), element.getName());
            if (items != null && !items.isEmpty()) {
              com.android.ide.common.res2.ResourceItem defaultValue = ThemeEditorUtils.getConfigurationForModule(myModule).getFullConfig().findMatchingConfigurable(items);
              // we may not have ANY value that works in current config
              editResourceItem(defaultValue == null ? items.get(0) : defaultValue);
              return;
            }
          }
        }

        if (element == null && myStateListPickerPanel != null && myStateListPickerPanel.getLocationSettings().getType() == myType &&
            myStateListPicker != null && myStateListPicker.getStateList() != null) {
          layout.show(myPreviewPanel, STATELIST);
          return;
        }

        if (element == null && myType == ResourceType.COLOR) {
          layout.show(myPreviewPanel, COLOR);
          return;
        }
      }

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      String doc =
        AndroidJavaDocRenderer.render(myModule, element.getGroup().getType(), element.getName(), element.getGroup().isFramework());
      myHtmlTextArea.setText(doc);
      layout.show(myPreviewPanel, TEXT);
    }

    public void editResourceItem(@NotNull com.android.ide.common.res2.ResourceItem selected) {
      String name = selected.getName();
      ResourceValue resourceValue = selected.getResourceValue(false);
      assert resourceValue != null;

      Configuration configuration = ThemeEditorUtils.getConfigurationForModule(myModule);
      final ResourceResolver resolver = configuration.getResourceResolver();
      assert resolver != null;

      @NotNull EditResourcePanel editResourcePanel;
      String panelName;
      ResourceHelper.StateList stateList = ResourceHelper.resolveStateList(resolver, resourceValue, myModule.getProject());
      if (stateList != null) { // if this is not a statelist, it may be just a normal color
        assert myStateListPickerPanel != null;
        assert myStateListPicker != null;

        if (stateList.getType() != myStateListPickerPanel.getLocationSettings().getType()) {
          LOG.warn("StateList type mismatch " + stateList.getType() + " " + myStateListPickerPanel.getLocationSettings().getType());
          // TODO need to make sure when we select this item that we know to preview it in html and not using the editor
          showPreview(getSelectedElement(), false);
          return;
        }
        myStateListPicker.setStateList(stateList);
        panelName = STATELIST;
        editResourcePanel = myStateListPickerPanel;
      }
      else {
        Color color = ResourceHelper.parseColor(resourceValue.getValue());
        if (color != null) { // if invalid color because of user error or a reference to another color
          assert myColorPickerPanel != null;
          assert myColorPicker != null;

          myColorPicker.setColor(color);
          panelName = COLOR;
          editResourcePanel = myColorPickerPanel;
        }
        else {
          // TODO we are prob a reference to another color, need to show edit reference/text value UI
          showPreview(getSelectedElement(), false);
          return;
        }
      }

      // TODO, if we are in the color panel, but we select the name of a colorStateList, then we need to display that we can't display the value

      editResourcePanel.getResourceNameField().setText(name); // this will update the dropdown, BUT ONLY after doValidate()
      editResourcePanel.doValidate(); // calls setVariant AND resetFromFile TO DEFAULT
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(selected.getFile());
      assert file != null;

      editResourcePanel.mySouthPanel.setSelectedVariant(selected);
      editResourcePanel.myLocationSettings.resetFromFile(file, myModule.getProject());

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, panelName);
    }

    @Nullable
    public ValidationInfo doValidate() {
      final boolean okActionEnabled;
      ValidationInfo error = null;

      if (myColorPickerPanel != null && myColorPickerPanel.isShowing()) {
        error = myColorPickerPanel.doValidate();
        okActionEnabled = error == null;
      }
      else if (myStateListPickerPanel != null && myStateListPickerPanel.isShowing()) {
        error = myStateListPickerPanel.doValidate();
        okActionEnabled = error == null;
      }
      else {
        okActionEnabled = getSelectedElement() != null;
      }

      // Need to always manually update the setOKActionEnabled as the DialogWrapper
      // only updates it if we go from having a error string to not having one
      // or the other way round, but not if the error string state has not changed.
      setOKActionEnabled(okActionEnabled);

      return error;
    }

    boolean supportsGridMode() {
      return myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE || myType == ResourceType.MIPMAP;
    }

    void setGridMode(boolean gridView) {
      if (gridView) {
        assert supportsGridMode();
        final ListCellRenderer gridRenderer = new DefaultListCellRenderer() {
          {
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalAlignment(SwingConstants.CENTER);
          }
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, false);
            // TODO show deprecated resources with a strikeout
            ResourceItem rItem = (ResourceItem) value;
            setIcon(ChooseResourceDialog.this.getIcon(rItem, JBUI.scale(80)));
            return component;
          }
        };
        myList.setFixedCellWidth(JBUI.scale(90));
        myList.setFixedCellHeight(JBUI.scale(100));
        myList.setCellRenderer(gridRenderer);
        myList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
      }
      else {
        final ListCellRenderer listRenderer = new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, false);
            // TODO show deprecated resources with a strikeout
            ResourceItem rItem = (ResourceItem) value;
            setIcon(ChooseResourceDialog.this.getIcon(rItem, JBUI.scale(28)));
            return component;
          }
        };
        myList.setFixedCellWidth(10); // we use ANY fixed value here, as the width will stretch anyway, but we don't want the list to have to calculate it.
        myList.setFixedCellHeight(JBUI.scale(32));
        myList.setCellRenderer(listRenderer);
        myList.setLayoutOrientation(JList.VERTICAL);
      }
    }

    /**
     * @param type can be {@link #COLOR} or {@link #STATELIST}
     */
    void showNewResource(@NotNull String type) {
      myList.setSelectedElement(null);
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, type);
    }

    private void select(@Nullable String namespace, @NotNull String name) {
      for (ResourceGroup group : myGroups) {
        if (Objects.equal(namespace, group.getNamespace())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.getName())) {
              myList.setSelectedElement(item);
              return;
            }
          }
        }
      }
    }

    public void expandAll() {
      myList.expandAll();
    }

    public ResourceItem getSelectedElement() {
      return (ResourceItem) myList.getSelectedElement();
    }

    @Nullable
    public String getValueForLivePreview() {
      if (myColorPickerPanel != null && myColorPickerPanel.isShowing()) {
        assert myColorPicker != null;
        return ResourceHelper.colorToString(myColorPicker.getColor());
      }
      ResourceItem element = getSelectedElement();
      return element != null ? element.getResourceUrl() : null;
    }

    // TODO this method can possibly be removed
    private void showComboPreview(@NotNull ResourceItem item) {
      // this assumes that item has more then 1 version
      long time = System.currentTimeMillis();
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;
      ResourceManager manager = facet.getResourceManager(item.getGroup().getNamespace());
      assert manager != null;
      List<ResourceElement> resources = manager.findValueResources(item.getGroup().getType().getName(), item.getName());
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
      }

      assert resources.size() > 1;

      resources = Lists.newArrayList(resources);
      Collections.sort(resources, new Comparator<ResourceElement>() {
        @Override
        public int compare(ResourceElement element1, ResourceElement element2) {
          PsiDirectory directory1 = element1.getXmlTag().getContainingFile().getParent();
          PsiDirectory directory2 = element2.getXmlTag().getContainingFile().getParent();

          if (directory1 == null && directory2 == null) {
            return 0;
          }
          if (directory2 == null) {
            return 1;
          }
          if (directory1 == null) {
            return -1;
          }

          return directory1.getName().compareTo(directory2.getName());
        }
      });

      DefaultComboBoxModel model = new DefaultComboBoxModel();
      String defaultSelection = null;
      for (int i = 0; i < resources.size(); i++) {
        ResourceElement resource = resources.get(i);
        PsiDirectory directory = resource.getXmlTag().getContainingFile().getParent();
        String name = directory == null ? "unknown-" + i : directory.getName();
        if (model.getIndexOf(name) >= 0) {
          // DefaultComboBoxModel uses a object (not a index) to keep the selected item, so each item needs to be unique
          Module module = resource.getModule();
          if (module != null) {
            name = name + " (" + module.getName() + ")";
          }
          if (model.getIndexOf(name) >= 0) {
            name = name + " (" + i + ")";
          }
        }
        model.addElement(name);
        if (defaultSelection == null && "values".equalsIgnoreCase(name)) {
          defaultSelection = name;
        }
      }

      String selection = (String)myComboBox.getSelectedItem();
      if (selection == null) {
        selection = defaultSelection;
      }

      int index = model.getIndexOf(selection);
      if (index == -1) {
        index = 0;
      }

      myComboBox.setModel(model);
      myComboBox.putClientProperty(COMBO, resources);
      myComboBox.setSelectedIndex(index);
      myComboTextArea.setText(getResourceElementValue(resources.get(index)));

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, COMBO);
    }
  }

  private class EditResourcePanel extends JBScrollPane {

    private ResourceDialogSouthPanel mySouthPanel;
    private ResourceNameValidator myValidator;
    private CreateXmlResourcePanel myLocationSettings;
    private ResourceNameVisibility myResourceNameVisibility = ResourceNameVisibility.HIDE;

    public EditResourcePanel(@NotNull JPanel centerPanel) {
      super(new JPanel(new BorderLayout()));
      getView().add(centerPanel);
      setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
    }

    @NotNull
    private JPanel getView() {
      return (JPanel)getViewport().getView();
    }

    @Override
    public Dimension getMinimumSize() {
      Insets insets = getInsets();
      return new Dimension(getView().getMinimumSize().width + insets.left + insets.right, super.getMinimumSize().height);
    }

    public void addResourceDialogSouthPanel(@NotNull ResourceNameVisibility resourceNameVisibility, @Nullable String resourceName,
                                            final boolean allowXmlFile, @NotNull ResourceFolderType folderType,
                                            boolean changeFileNameVisible, final @NotNull ResourceType resourceType) {
      assert resourceNameVisibility != ResourceNameVisibility.HIDE;
      myResourceNameVisibility = resourceNameVisibility;
      mySouthPanel = new ResourceDialogSouthPanel();
      myLocationSettings = new CreateXmlResourcePanel(myModule, resourceType, null, folderType);
      if (resourceName != null) {
        mySouthPanel.getResourceNameField().setText(resourceName);
      }
      // if the resource name IS the filename, we don't need to allow changing the filename
      myLocationSettings.setChangeFileNameVisible(changeFileNameVisible);

      getView().add(mySouthPanel.getFullPanel(), BorderLayout.SOUTH);

      mySouthPanel.setExpertPanel(myLocationSettings.getPanel());
      myLocationSettings.addModuleComboActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Module module = myLocationSettings.getModule();
          assert module != null;
          myValidator = ResourceNameValidator
            .create(allowXmlFile, AppResourceRepository.getAppResources(module, true), resourceType, allowXmlFile);
        }
      });

      myValidator = ResourceNameValidator
        .create(allowXmlFile, AppResourceRepository.getAppResources(myModule, true), resourceType, allowXmlFile);

      mySouthPanel.addVariantActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          // user has selected a different variant for the current resource, so we need to display it
          getSelectedPanel().editResourceItem(mySouthPanel.getSelectedVariant());
        }
      });
    }

    @NotNull
    public JLabel getResourceNameMessage() {
      return mySouthPanel.getResourceNameMessage();
    }

    @NotNull
    public JTextField getResourceNameField() {
      return mySouthPanel.getResourceNameField();
    }

    @NotNull
    public CreateXmlResourcePanel getLocationSettings() {
      return myLocationSettings;
    }

    public void openLocationSettings() {
      mySouthPanel.setOn(true);
    }

    @Nullable
    public ValidationInfo doValidate() {
      ValidationInfo error = null;
      String overwriteResource = "";

      // if name is hidden, then we allow any value
      if (myResourceNameVisibility == ResourceNameVisibility.FORCE ||
          (myResourceNameVisibility == ResourceNameVisibility.SHOW && !getResourceNameField().getText().isEmpty())) {
        if (myValidator != null) {
          String enteredName = getResourceNameField().getText();
          if (myValidator.doesResourceExist(enteredName)) {
            overwriteResource = String.format("Saving this color will override existing resource %1$s.", enteredName);
          }
          else {
            String errorText = myValidator.getErrorText(enteredName);
            if (errorText != null) {
              error = new ValidationInfo(errorText, getResourceNameField());
            }
          }

          // the name of the resource must have changed, lets re-load the variants.
          if (!overwriteResource.equals(getResourceNameMessage().getText())) {
            AndroidFacet facet = AndroidFacet.getInstance(myModule);
            assert facet != null;
            List<com.android.ide.common.res2.ResourceItem> resources = facet.getAppResources(true).getResourceItem(myLocationSettings.getType(), enteredName);
            com.android.ide.common.res2.ResourceItem defaultValue = ThemeEditorUtils.getConfigurationForModule(myModule).getFullConfig().findMatchingConfigurable(resources);
            if (defaultValue != null) {
              VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(defaultValue.getFile());
              assert file != null;
              myLocationSettings.resetFromFile(file, myModule.getProject());
            }
            mySouthPanel.setVariant(resources == null ? Collections.<com.android.ide.common.res2.ResourceItem>emptyList() : resources, defaultValue);
          }
        }

        if (error == null) {
          error = getLocationSettings().doValidate();
        }
      }

      if (!overwriteResource.equals(getResourceNameMessage().getText())) {
        getResourceNameMessage().setText(overwriteResource);
      }
      return error;
    }
  }

  public static class ResourceGroup {
    private List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final String myNamespace;
    private final ResourceType myType;

    public ResourceGroup(@Nullable String namespace, @NotNull ResourceType type, @NotNull ResourceManager manager, boolean includeFileResources) {
      myType = type;
      myNamespace = namespace;

      final String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, null));
      }
      final Set<String> fileNames = new HashSet<String>();

      if (includeFileResources) {
        manager.processFileResources(resourceType, new FileResourceProcessor() {
          @Override
          public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
            if (fileNames.add(resName)) {
              myItems.add(new ResourceItem(ResourceGroup.this, resName, resFile));
            }
            return true;
          }
        });
      }

      if (type == ResourceType.ID) {
        for (String id : manager.getIds(true)) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, null));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.getName().compareTo(resource2.getName());
        }
      });
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    @Nullable("null for app namespace")
    public String getNamespace() {
      return myNamespace;
    }

    public List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myNamespace == null ? APP_NAMESPACE_LABEL : myNamespace;
    }

    public boolean isFramework() {
      return SdkConstants.ANDROID_NS_NAME.equals(getNamespace());
    }
  }

  public static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;
    private final VirtualFile myFile;
    private Icon myIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, @Nullable VirtualFile file) {
      myGroup = group;
      myName = name;
      myFile = file;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myName;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    public String getResourceUrl() {
      return String
        .format("@%s%s/%s", getGroup().getNamespace() == null ? "" : getGroup().getNamespace() + ":", myGroup.getType().getName(), myName);
    }

    @NotNull
    public ResourceValue getResourceValue() {
      // No need to try and find the resource as we know exactly what it's going to be like
      return new ResourceValue(myGroup.getType(), getName(), myFile == null ? getResourceUrl() : myFile.getPath(), myGroup.isFramework());
    }

    @Override
    public String toString() {
      // we need to return JUST the name so quicksearch in JList works
      return getName();
    }

    @Nullable("if no icon has been set on this item")
    public Icon getIcon() {
      return myIcon;
    }

    public void setIcon(@Nullable Icon icon) {
      myIcon = icon;
    }
  }

  private static class TreeContentProvider extends AbstractTreeStructure {
    private final Object myTreeRoot = new Object();
    private final ResourceGroup[] myGroups;

    public TreeContentProvider(ResourceGroup[] groups) {
      myGroups = groups;
    }

    @Override
    public Object getRootElement() {
      return myTreeRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == myTreeRoot) {
        return myGroups;
      }
      if (element instanceof ResourceGroup) {
        ResourceGroup group = (ResourceGroup)element;
        return group.getItems().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof ResourceItem) {
        ResourceItem resource = (ResourceItem)element;
        return resource.getGroup();
      }
      return null;
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }

  public static class SizedIcon implements Icon {
    private final int mySize;
    private final Image myImage;

    public SizedIcon(int size, Image image) {
      mySize = size;
      myImage = image;
    }

    public SizedIcon(int size, ImageIcon icon) {
      this(size, icon.getImage());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int i, int j) {
      double scale = Math.min(getIconHeight()/(double)myImage.getHeight(c),getIconWidth()/(double)myImage.getWidth(c));
      int x = (int) (getIconWidth() - (myImage.getWidth(c) * scale)) / 2;
      int y = (int) (getIconHeight() - (myImage.getHeight(c) * scale)) / 2;
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(myImage, i + x, j + y, (int) (myImage.getWidth(c) * scale), (int) (myImage.getHeight(c) * scale), null);
    }

    @Override
    public int getIconWidth() {
      return mySize;
    }

    @Override
    public int getIconHeight() {
      return mySize;
    }
  }

  private static class SimpleTabUI extends BasicTabbedPaneUI {

    @Override
    protected void installDefaults() {
      super.installDefaults();
      tabInsets = JBUI.insets(8);
      selectedTabPadInsets = JBUI.emptyInsets();
      contentBorderInsets = JBUI.emptyInsets();
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
      // dont want tab border
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
      // dont want a background
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
      int width = tabPane.getWidth();
      int height = tabPane.getHeight();
      Insets insets = tabPane.getInsets();

      int x = insets.left;
      int y = insets.top;
      int w = width - insets.right - insets.left;
      int h = height - insets.top - insets.bottom;

      int thickness = JBUI.scale(1);
      g.setColor(OnePixelDivider.BACKGROUND);

      // use fillRect instead of drawLine with thickness as drawLine has bugs on OS X retina
      switch(tabPlacement) {
        case LEFT:
          x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
          g.fillRect(x - thickness, y, thickness, h);
          break;
        case RIGHT:
          w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
          g.fillRect(x + w, y, thickness, h);
          break;
        case BOTTOM:
          h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
          g.fillRect(x, y + h, w, thickness);
          break;
        case TOP:
        default:
          y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
          g.fillRect(x, y - thickness, w, thickness);
      }
    }

    @Override
    protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
      return super.getTabLabelShiftX(tabPlacement, tabIndex, false);
    }

    @Override
    protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
      return super.getTabLabelShiftY(tabPlacement, tabIndex, false);
    }

    @Override
    protected void layoutLabel(int tabPlacement,
                               FontMetrics metrics, int tabIndex,
                               String title, Icon icon,
                               Rectangle tabRect, Rectangle iconRect,
                               Rectangle textRect, boolean isSelected ) {
      textRect.x = textRect.y = iconRect.x = iconRect.y = 0;

      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        tabPane.putClientProperty("html", v);
      }

      // CHANGE FROM DEFAULT: take tab insets into account
      Insets insets = getTabInsets(tabPlacement, tabIndex);
      tabRect = new Rectangle(tabRect);
      tabRect.x += insets.left;
      tabRect.y += insets.top;
      tabRect.width = tabRect.width - insets.left - insets.right;
      tabRect.height = tabRect.height - insets.top - insets.bottom;

      SwingUtilities.layoutCompoundLabel(tabPane,
                                         metrics, title, icon,
                                         SwingConstants.CENTER,
                                         SwingConstants.LEADING, // CHANGE FROM DEFAULT
                                         SwingConstants.CENTER,
                                         SwingConstants.TRAILING,
                                         tabRect,
                                         iconRect,
                                         textRect,
                                         textIconGap);

      tabPane.putClientProperty("html", null);

      int xNudge = getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
      int yNudge = getTabLabelShiftY(tabPlacement, tabIndex, isSelected);
      iconRect.x += xNudge;
      iconRect.y += yNudge;
      textRect.x += xNudge;
      textRect.y += yNudge;
    }

    @Override
    protected void paintText(Graphics g, int tabPlacement,
                             Font font, FontMetrics metrics, int tabIndex,
                             String title, Rectangle textRect,
                             boolean isSelected) {

      g.setFont(font);

      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        // html
        v.paint(g, textRect);
      } else {
        // plain text
        int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

        if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
          Color fg = tabPane.getForegroundAt(tabIndex);
          if (isSelected && (fg instanceof UIResource)) {
            Color selectedFG = JBColor.BLUE; // CHANGE FROM DEFAULT
            if (selectedFG != null) {
              fg = selectedFG;
            }
          }
          g.setColor(fg);
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x, textRect.y + metrics.getAscent());
        } else { // tab disabled
          g.setColor(tabPane.getBackgroundAt(tabIndex).brighter());
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x, textRect.y + metrics.getAscent());
          g.setColor(tabPane.getBackgroundAt(tabIndex).darker());
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x - 1, textRect.y + metrics.getAscent() - 1);
        }
      }
    }
  }
}
