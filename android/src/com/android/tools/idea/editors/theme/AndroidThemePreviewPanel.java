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
package com.android.tools.idea.editors.theme;


import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.editors.theme.ThemePreviewBuilder.ComponentDefinition;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.android.tools.swing.ui.NavigationComponent;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * Component that renders a theme preview. The preview size is independent of the selected device DPI so controls size will remain constant
 * for all devices.
 * Implements RenderContext to support Configuration toolbar in Theme Editor.
 */
public class AndroidThemePreviewPanel extends Box implements RenderContext {

  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class);
  // The scaling factor is based on how we want the preview to look for different devices. 160 means that a device with 160 DPI would look
  // exactly as GraphicsLayoutRenderer would render it. A device with 300 DPI would usually look smaller but because we apply this scaling
  // factor, it would be scaled 2x to look exactly as the 100 DPI version would look.
  private static final double DEFAULT_SCALING_FACTOR = 160.0;

  private static final Map<String, ComponentDefinition> SUPPORT_LIBRARY_COMPONENTS =
    ImmutableMap.of("android.support.design.widget.FloatingActionButton",
                    new ComponentDefinition("Fab", ThemePreviewBuilder.ComponentGroup.FAB_BUTTON,
                                            "android.support.design.widget.FloatingActionButton")
                      .set("src", "@drawable/abc_ic_ab_back_mtrl_am_alpha")
                      .set("clickable", "true"));

  /** Min API level to use for filtering. */
  private int myMinApiLevel;
  /** Current search term to use for filtering. If empty, no search term is being used */
  private String mySearchTerm = "";
  /** Cache of the custom components found on the project */
  private List<ComponentDefinition> myCustomComponents = Collections.emptyList();

  protected final Configuration myConfiguration;
  protected final NavigationComponent<Breadcrumb> myBreadcrumbs;
  protected final SearchTextField mySearchTextField;
  protected final AndroidPreviewPanel myAndroidPreviewPanel;

  private final ScheduledExecutorService mySearchScheduler = Executors.newSingleThreadScheduledExecutor();
  /** Next pending search. The {@link ScheduledFuture} allows us to cancel the next search before it runs. */
  private ScheduledFuture<?> myPendingSearch;

  protected DumbService myDumbService;

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
  private double myConstantScalingFactor = DEFAULT_SCALING_FACTOR;

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


  public AndroidThemePreviewPanel(@NotNull final Configuration configuration) {
    super(BoxLayout.PAGE_AXIS);

    myConfiguration = configuration;
    myAndroidPreviewPanel = new AndroidPreviewPanel(configuration);

    myBreadcrumbs = new NavigationComponent<Breadcrumb>();
    myBreadcrumbs.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    myBreadcrumbs.setOpaque(false);

    myDumbService = DumbService.getInstance(myConfiguration.getModule().getProject());

    myMinApiLevel = configuration.getTarget() != null ? configuration.getTarget().getVersion().getApiLevel() : Integer.MAX_VALUE;

    JBScrollPane scrollPanel = new JBScrollPane(myAndroidPreviewPanel,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    mySearchTextField = new SearchTextField(true);
    // Avoid search box stretching more than 1 line.
    mySearchTextField.setMaximumSize(new Dimension(Integer.MAX_VALUE, mySearchTextField.getPreferredSize().height));
    final Runnable delayedUpdate = new Runnable() {
      @Override
      public void run() {
        rebuild();
      }
    };

    // We use a timer when we detect a change in the search field to avoid re-creating the preview if it's not necessary.
    mySearchTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        Document document = e.getDocument();
        try {
          String search = document.getText(0, document.getLength());

          // Only use search terms longer than 3 characters.
          String newSearchTerm = search.length() < 3 ? "" : search;
          if (newSearchTerm.equals(mySearchTerm)) {
            return;
          }
          if (myPendingSearch != null) {
            myPendingSearch.cancel(true);
          }
          mySearchTerm = newSearchTerm;
          myPendingSearch = mySearchScheduler.schedule(delayedUpdate, 300, TimeUnit.MILLISECONDS);
        }
        catch (BadLocationException e1) {
          LOG.error(e1);
        }
      }
    });

    myBreadcrumbs.setRootItem(new Breadcrumb("All components"));

    add(myBreadcrumbs);
    add(mySearchTextField);
    add(scrollPanel);

    reloadComponents();

    myBreadcrumbs.addItemListener(new NavigationComponent.ItemListener<Breadcrumb>() {
      @Override
      public void itemSelected(@NotNull Breadcrumb item) {
        myBreadcrumbs.goTo(item);
        rebuild();
      }
    });

    myAndroidPreviewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ViewInfo view = myAndroidPreviewPanel.findViewAtPoint(e.getPoint());

        if (view == null) {
          return;
        }

        mySearchTextField.setText("");

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

  @NotNull
  public Set<String> getUsedAttrs() {
    return myAndroidPreviewPanel.getUsedAttrs();
  }

  /**
   * Searches the PSI for both custom components and support library classes. Call this method when you
   * want to refresh the components displayed on the preview.
   */
  public void reloadComponents() {
    myDumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        Project project = myConfiguration.getModule().getProject();
        if (!project.isOpen()) {
          return;
        }
        PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

        if (viewClass == null) {
          LOG.error("Unable to find 'android.view.View'");
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

            customComponents
              .add(new ComponentDefinition(description, ThemePreviewBuilder.ComponentGroup.CUSTOM, className));
            return true;
          }
        });

        // Now search for support library components.
        viewClasses = ClassInheritorsSearch.search(viewClass, ProjectScope.getLibrariesScope(project), true);
        viewClasses.forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            String className = psiClass.getQualifiedName();

            ComponentDefinition component = SUPPORT_LIBRARY_COMPONENTS.get(className);
            if (component != null) {
              customComponents.add(component);
            }

            return true;
          }
        });

        myCustomComponents = Collections.unmodifiableList(customComponents);
        if (!myCustomComponents.isEmpty()) {
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
      ThemePreviewBuilder builder = new ThemePreviewBuilder()
        .setBackgroundColor(UIUtil.getPanelBackground()) // Separator to give the appearance of cards
        .addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS)
        .addAllComponents(myCustomComponents)
        .addComponentFilter(new ThemePreviewBuilder.SearchFilter(mySearchTerm))
        .addComponentFilter(new ThemePreviewBuilder.ApiLevelFilter(myMinApiLevel))
        .addComponentFilter(myGroupFilter);
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

  public void updateConfiguration(@NotNull Configuration configuration) {
    myAndroidPreviewPanel.updateConfiguration(configuration);
    // We want the preview to remain the same size even when the device being used to render is different.
    // Adjust the scale to the current config.
    if (myConfiguration.getDeviceState() != null) {
      double scale = myConstantScalingFactor / myConfiguration.getDeviceState().getHardware().getScreen().getPixelDensity().getDpiValue();
      myAndroidPreviewPanel.setScale(scale);
    } else {
      LOG.error("Configuration getDeviceState returned null. Unable to set preview scale.");
    }
  }

  /**
   * Sets the preview scale to allow zooming in and out. Even when zoom (scale != 1.0) is set, different devices will still render to the
   * same size as the theme preview renderer is DPI independent.
   */
  public void setScale(double scale) {
    myConstantScalingFactor = DEFAULT_SCALING_FACTOR * scale;
  }

  /**
   * Tells the panel that it needs to reload its android content and repaint it.
   */
  public void invalidateGraphicsRenderer() {
    myAndroidPreviewPanel.invalidateGraphicsRenderer();
    myAndroidPreviewPanel.repaint();
  }

  // Implements RenderContext
  // Only methods relevant to the configuration selection have been implemented

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {

  }

  @Override
  public void requestRender() {

  }

  @NotNull
  @Override
  public UsageType getType() {
    return UsageType.UNKNOWN;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return null;
  }

  @Nullable
  @Override
  public Module getModule() {
    return null;
  }

  @Override
  public boolean hasAlphaChannel() {
    return false;
  }

  @NotNull
  @Override
  public Component getComponent() {
    return this;
  }

  @NotNull
  @Override
  public Dimension getFullImageSize() {
    return NO_SIZE;
  }

  @NotNull
  @Override
  public Dimension getScaledImageSize() {
    return NO_SIZE;
  }

  @NotNull
  @Override
  public Rectangle getClientArea() {
    return new Rectangle();
  }

  @Override
  public boolean supportsPreviews() {
    return false;
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    return null;
  }

  @Override
  public void setMaxSize(int width, int height) {

  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {

  }

  @Override
  public void updateLayout() {

  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {

  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    return null;
  }

  @Nullable
  @Override
  public RenderResult getLastResult() {
    return null;
  }

  @Nullable
  @Override
  public RenderedViewHierarchy getViewHierarchy() {
    return null;
  }
}
