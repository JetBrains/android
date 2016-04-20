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
package com.android.tools.idea.editors.theme.preview;


import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.preview.ThemePreviewBuilder.ComponentDefinition;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.android.tools.swing.ui.NavigationComponent;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;


/**
 * Component that renders a theme preview. The preview size is independent of the selected device DPI so controls size will remain constant
 * for all devices.
 * Implements RenderContext to support Configuration toolbar in Theme Editor.
 */
public class AndroidThemePreviewPanel extends Box implements ConfigurationHolder, Disposable {
  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class);

  private static final Map<String, ComponentDefinition> SUPPORT_LIBRARY_COMPONENTS =
    ImmutableMap.of("android.support.design.widget.FloatingActionButton",
                    new ComponentDefinition("Fab", ThemePreviewBuilder.ComponentGroup.FAB_BUTTON,
                                            "android.support.design.widget.FloatingActionButton")
                      .set("src", "@drawable/abc_ic_ab_back_mtrl_am_alpha")
                      .set("layout_width", "56dp")
                      .set("layout_height", "56dp"),
                    "android.support.v7.widget.Toolbar",
                    new ToolbarComponentDefinition(true/*isAppCompat*/));
  private static final Map<String, String> SUPPORT_LIBRARY_REPLACEMENTS =
    ImmutableMap.of("android.support.v7.widget.Toolbar", "Toolbar");
  /** Enable the component drill down that allows to see only selected groups of components on click */
  private static final boolean ENABLE_COMPONENTS_DRILL_DOWN = false;
  private static final String ERROR = "Error";
  private static final String PROGRESS = "Progress";
  private static final String PREVIEW = "Preview";

  private final JPanel myMainPanel;
  private Box myErrorPanel;
  private JTextPane myErrorLabel;
  private Box myProgressPanel;
  private AsyncProcessIcon myProgressIcon;

  /** Current search term to use for filtering. If empty, no search term is being used */
  private String mySearchTerm = "";
  /** Cache of the custom components found on the project */
  private List<ComponentDefinition> myCustomComponents = Collections.emptyList();
  /** List of components on the support library (if available) */
  private List<ComponentDefinition> mySupportLibraryComponents = Collections.emptyList();

  /** List of component names that shouldn't be displayed. This is used in the case where a support component supersedes a framework one. */
  private final List<String> myDisabledComponents = new ArrayList<String>();

  private final ThemeEditorContext myContext;
  protected final NavigationComponent<Breadcrumb> myBreadcrumbs;
  protected final AndroidPreviewPanel myAndroidPreviewPanel;
  protected final JBScrollPane myScrollPane;

  protected final DumbService myDumbService;

  /** Filters components that are disabled or that do not belong to the current selected group */
  private final Predicate<ComponentDefinition> myGroupFilter = new Predicate<ComponentDefinition>() {
    @Override
    public boolean apply(@Nullable ComponentDefinition input) {
      if (input == null) {
        return false;
      }

      Breadcrumb breadcrumb = myBreadcrumbs.peek();
      return (breadcrumb == null || breadcrumb.myGroup == null || breadcrumb.myGroup.equals(input.group));
    }
  };
  private final Predicate<ComponentDefinition> mySupportReplacementsFilter = new Predicate<ComponentDefinition>() {
    @Override
    public boolean apply(@Nullable ComponentDefinition input) {
      if (input == null) {
        return false;
      }

      return !myDisabledComponents.contains(input.name);
    }
  };

  private float myScale = 1;
  private boolean myIsAppCompatTheme = false;
  private boolean myShowError = false;

  static class Breadcrumb extends NavigationComponent.Item {
    private final ThemePreviewBuilder.ComponentGroup myGroup;
    private final String myDisplayText;

    private Breadcrumb(@NotNull String name, @Nullable ThemePreviewBuilder.ComponentGroup group) {
      myDisplayText = name;
      myGroup = group;
    }

    public Breadcrumb(@NotNull String name) {
      this(name, null);
    }

    public Breadcrumb(@NotNull ThemePreviewBuilder.ComponentGroup group) {
      this(group.name, group);
    }

    @Override
    @NotNull
    public String getDisplayText() {
      return myDisplayText;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Breadcrumb that = (Breadcrumb)o;
      return Objects.equal(myGroup, that.myGroup) && Objects.equal(myDisplayText, that.myDisplayText);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myGroup, myDisplayText);
    }
  }

  public AndroidThemePreviewPanel(@NotNull ThemeEditorContext context, @NotNull Color background) {
    super(BoxLayout.PAGE_AXIS);

    setOpaque(true);
    setMinimumSize(JBUI.size(200, 0));

    myContext = context;
    myContext.getProject().getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateMainPanel();
      }

      @Override
      public void exitDumbMode() {
        updateMainPanel();
      }
    });

    myAndroidPreviewPanel = new AndroidPreviewPanel(myContext.getConfiguration());
    myContext.addChangeListener(new ThemeEditorContext.ChangeListener() {
      @Override
      public void onNewConfiguration(ThemeEditorContext context) {
        refreshConfiguration();
      }
    });
    myAndroidPreviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

    myBreadcrumbs = new NavigationComponent<Breadcrumb>();
    myBreadcrumbs.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));

    myDumbService = DumbService.getInstance(context.getProject());

    myScrollPane = new JBScrollPane(myAndroidPreviewPanel,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    // We use an empty border instead of null, because a null border will be overridden by a change of UI,
    // while an empty border will stay an empty border.
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myScrollPane.setViewportBorder(null);

    myBreadcrumbs.setRootItem(new Breadcrumb("All components"));

    createErrorPanel();
    createProgressPanel();

    add(Box.createRigidArea(JBUI.size(0, 5)));
    add(myBreadcrumbs);
    add(Box.createRigidArea(JBUI.size(0, 10)));

    myMainPanel = new JPanel(new CardLayout());
    myMainPanel.add(myErrorPanel, ERROR);
    myMainPanel.add(myProgressPanel, PROGRESS);
    myMainPanel.add(myScrollPane, PREVIEW);
    add(myMainPanel);

    setBackground(background);
    reloadComponents();

    myBreadcrumbs.addItemListener(new NavigationComponent.ItemListener<Breadcrumb>() {
      @Override
      public void itemSelected(@NotNull Breadcrumb item) {
        myBreadcrumbs.goTo(item);
        rebuild();
      }
    });

    if (ENABLE_COMPONENTS_DRILL_DOWN) {
      myAndroidPreviewPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          ViewInfo view = myAndroidPreviewPanel.findViewAtPoint(e.getPoint());

          if (view == null) {
            return;
          }

          Object cookie = view.getCookie();
          if (cookie instanceof MergeCookie) {
            cookie = ((MergeCookie)cookie).getCookie();
          }

          if (!(cookie instanceof Element)) {
            return;
          }

          NamedNodeMap attributes = ((Element)cookie).getAttributes();
          Node group = attributes.getNamedItemNS(ThemePreviewBuilder.BUILDER_URI, ThemePreviewBuilder.BUILDER_ATTR_GROUP);

          if (group != null) {
            myBreadcrumbs.push(new Breadcrumb(ThemePreviewBuilder.ComponentGroup.valueOf(group.getNodeValue())));
            rebuild();
          }
        }
      });
    }

    myContext.addConfigurationListener(new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {
        refreshConfiguration();

        if (ThemeEditorUtils.isSelectedAppCompatTheme(myContext) != myIsAppCompatTheme) {
          rebuild();
        }

        return true;
      }
    });

    refreshScale();
    updateMainPanel();
  }

  /**
   * Chooses the correct panel to display between the progress panel, the error panel or the preview panel
   */
  private void updateMainPanel() {
    if (myDumbService.isDumb()) {
      myProgressIcon.resume();
      ((CardLayout)myMainPanel.getLayout()).show(myMainPanel, PROGRESS);
    }
    else {
      myProgressIcon.suspend();
      ((CardLayout)myMainPanel.getLayout()).show(myMainPanel, myShowError ? ERROR : PREVIEW);
    }
  }

  /**
   * Set a search term to filter components in a preview, will trigger a delayed update.
   */
  public void setSearchTerm(@NotNull String searchTerm) {
    if (searchTerm.equals(mySearchTerm)) {
      return;
    }
    mySearchTerm = searchTerm;
    rebuild();
  }

  @Override
  public void setBackground(Color bg) {
    if(Objects.equal(bg, getBackground())) {
      return;
    }
    super.setBackground(bg);

    myAndroidPreviewPanel.setBackground(bg);
    myScrollPane.getViewport().setBackground(bg);
    myBreadcrumbs.setBackground(bg);
    myMainPanel.setBackground(bg);

    // Necessary so that the preview uses the updated background color,
    // since the background of the preview is set when it is built.
    rebuild();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    rebuild();
  }

  /**
   * Searches the PSI for both custom components and support library classes. Call this method when you
   * want to refresh the components displayed on the preview.
   */
  public void reloadComponents() {
    myDumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        Project project = myContext.getProject();
        if (!project.isOpen()) {
          return;
        }
        PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

        if (viewClass == null) {
          // There is probably no SDK
          LOG.debug("Unable to find 'android.view.View'");
          return;
        }

        Query<PsiClass> viewClasses = ClassInheritorsSearch.search(viewClass, ProjectScope.getProjectScope(project), true);
        final ArrayList<ComponentDefinition> customComponents =
          new ArrayList<ComponentDefinition>();
        viewClasses.forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            String description = psiClass.getName(); // We use the "simple" name as description on the preview.
            String className = psiClass.getQualifiedName();

            if (description == null || className == null) {
              // Currently we ignore anonymous views
              return false;
            }

            customComponents.add(new ComponentDefinition(description, ThemePreviewBuilder.ComponentGroup.CUSTOM, className));
            return true;
          }
        });

        // Now search for support library components. We use a HashSet to avoid adding duplicate components from source and jar files.
        myDisabledComponents.clear();
        final HashSet<ComponentDefinition> supportLibraryComponents = new HashSet<ComponentDefinition>();
        viewClasses = ClassInheritorsSearch.search(viewClass, ProjectScope.getLibrariesScope(project), true);
        viewClasses.forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            String className = psiClass.getQualifiedName();

            ComponentDefinition component = SUPPORT_LIBRARY_COMPONENTS.get(className);
            if (component != null) {
              supportLibraryComponents.add(component);

              String replaces = SUPPORT_LIBRARY_REPLACEMENTS.get(className);
              if (replaces != null) {
                myDisabledComponents.add(replaces);
              }
            }

            return true;
          }
        });

        myCustomComponents = Collections.unmodifiableList(customComponents);
        mySupportLibraryComponents = ImmutableList.copyOf(supportLibraryComponents);
        if (!myCustomComponents.isEmpty() || !mySupportLibraryComponents.isEmpty()) {
          rebuild();
        }
      }
    });

    rebuild(false);
  }

  /**
   * Rebuild the preview
   * @param forceRepaint if true, a component repaint will be issued
   */
  private void rebuild(boolean forceRepaint) {
    try {
      Configuration configuration = myContext.getConfiguration();
      int minApiLevel = configuration.getTarget() != null ? configuration.getTarget().getVersion().getApiLevel() : Integer.MAX_VALUE;

      ThemePreviewBuilder builder = new ThemePreviewBuilder()
        .setBackgroundColor(getBackground()).addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS)
        .addNavigationBar(configuration.supports(Features.THEME_PREVIEW_NAVIGATION_BAR))
        .addAllComponents(myCustomComponents)
        .addComponentFilter(new ThemePreviewBuilder.SearchFilter(mySearchTerm))
        .addComponentFilter(new ThemePreviewBuilder.ApiLevelFilter(minApiLevel))
        .addComponentFilter(myGroupFilter);

      myIsAppCompatTheme = ThemeEditorUtils.isSelectedAppCompatTheme(myContext);
      if (myIsAppCompatTheme) {
        builder
          .addComponentFilter(mySupportReplacementsFilter)
          .addAllComponents(mySupportLibraryComponents);

        // sometimes we come here when the mySupportLibraryComponents and the mySupportReplacementsFilter are not ready yet
        // that is not too bad, as when they are ready, we will call reload again, and then the components list will be correct.
      }
      myAndroidPreviewPanel.setDocument(builder.build());

      if (forceRepaint) {
        repaint();
      }
    }
    catch (ParserConfigurationException e) {
      LOG.error("Unable to generate dynamic theme preview", e);
    }
  }

  /**
   * Rebuild the preview and repaint
   */
  private void rebuild() {
    rebuild(true);
  }

  private void refreshScale() {
    Configuration configuration = myContext.getConfiguration();

    // We want the preview to remain the same size even when the device being used to render is different.
    // Adjust the scale to the current config.
    if (configuration.getDeviceState() != null) {
      float reverseDeviceScale =
        Density.DEFAULT_DENSITY / (float)configuration.getDeviceState().getHardware().getScreen().getPixelDensity().getDpiValue();
      // we combine our scale, the reverse device scale, and the platform scale into 1 scale factor.
      myAndroidPreviewPanel.setScale(JBUI.scale(reverseDeviceScale * myScale));
    }
  }

  private void refreshConfiguration() {
    Configuration configuration = myContext.getConfiguration();

    myAndroidPreviewPanel.updateConfiguration(configuration);
    refreshScale();
  }

  private void createProgressPanel() {
    myProgressIcon = new AsyncProcessIcon("Indexing");
    Disposer.register(this, myProgressIcon);
    JLabel progressMessage = new JLabel("Waiting for indexing...");
    JPanel progressBlock = new JPanel() {
      @Override
      public Dimension getMaximumSize() {
        // Ensures this pane will always be only as big as the text it contains.
        // Necessary to vertically center it inside a Box.
        return super.getPreferredSize();
      }
    };

    progressBlock.add(myProgressIcon);
    progressBlock.add(progressMessage);
    progressBlock.setOpaque(false);

    myProgressPanel = new Box(BoxLayout.PAGE_AXIS);
    myProgressPanel.add(Box.createVerticalGlue());
    myProgressPanel.add(progressBlock);
    myProgressPanel.add(Box.createVerticalGlue());

    myProgressPanel.setOpaque(false);
  }

  private void createErrorPanel() {
    myErrorLabel = new JTextPane() {
      @Override
      public Dimension getMaximumSize() {
        // Ensures this pane will always be only as big as the text it contains.
        // Necessary to vertically center it inside a Box.
        return super.getPreferredSize();
      }
    };
    myErrorLabel.setOpaque(false);

    myErrorPanel = new Box(BoxLayout.PAGE_AXIS);
    myErrorPanel.add(Box.createVerticalGlue());
    myErrorPanel.add(myErrorLabel);
    myErrorPanel.add(Box.createVerticalGlue());

    myErrorPanel.setOpaque(false);

    StyledDocument document = myErrorLabel.getStyledDocument();
    SimpleAttributeSet attributes = new SimpleAttributeSet();
    StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_CENTER);
    document.setParagraphAttributes(0, document.getLength(), attributes, false);
  }

  public void setErrorMessage(@Nullable String errorMessage) {
    myShowError = errorMessage != null;
    if (errorMessage != null) {
      myErrorLabel.setText(errorMessage);
    }
    updateMainPanel();
  }

  /**
   * Sets the preview scale to allow zooming in and out. Even when zoom (scale != 1.0) is set, different devices will still render to the
   * same size as the theme preview renderer is DPI independent.
   */
  public void setScale(float scale) {
    myScale = scale;
  }

  /**
   * Tells the panel that it needs to reload its android content and repaint it.
   */
  public void invalidateGraphicsRenderer() {
    myAndroidPreviewPanel.invalidateGraphicsRenderer();
  }

  @Override
  public void dispose() {
  }

  // Implements ConfigurationHolder
  // Only methods relevant to the configuration selection have been implemented

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return myContext.getConfiguration();
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
    // This might be called when the user is forcing a configuration on the current view
    if (myContext != null) {
      myContext.setConfiguration(configuration);
    }
  }
}
