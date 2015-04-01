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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * Component that renders a theme preview.
 * Implements RenderContext to support Configuration toolbar in Theme Editor
 */
public class AndroidThemePreviewPanel extends Box implements RenderContext {

  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class);

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
    rebuild(false/*forceRepaint*/);

    JBScrollPane scrollPanel = new JBScrollPane(myAndroidPreviewPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    /*
     * Set a preferred size for the preview panel. Since we are using HORIZONTAL_SCROLLBAR_NEVER, the width will be ignored and the panel
     * size used.
     * The height should be set according to a reasonable space to display the preview layout.
     *
     * TODO: Check the height value.
     */
    myAndroidPreviewPanel.setPreferredSize(new Dimension(64, 2000));

    mySearchTextField = new SearchTextField(true);
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

    // Find custom controls
    myDumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        Project project = configuration.getModule().getProject();
        if (!project.isOpen()) {
          return;
        }
        PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

        if (viewClass == null) {
          LOG.error("Unable to find 'android.view.View'");
          return;
        }
        Query<PsiClass> viewClasses = ClassInheritorsSearch.search(viewClass, GlobalSearchScope.projectScope(project), true);
        final ArrayList<ComponentDefinition> customComponents =
          new ArrayList<ComponentDefinition>();
        viewClasses.forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            String description = psiClass.getName(); // We use the "simple" name as description on the preview.
            String className = psiClass.getQualifiedName();

            if (description == null || className == null) {
              // Currently we ignore anonymous views
              // TODO: Decide how we want to display anonymous classes
              return false;
            }

            customComponents
              .add(new ComponentDefinition(description, ThemePreviewBuilder.ComponentGroup.CUSTOM, className));
            return true;
          }
        });

        myCustomComponents = Collections.unmodifiableList(customComponents);
        if (!myCustomComponents.isEmpty()) {
          rebuild();
        }
      }
    });

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

        if (view.getCookie() == null) {
          return;
        }
        NamedNodeMap attributes = ((Element)view.getCookie()).getAttributes();
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
   * Rebuild the preview
   * @param forceRepaint if true, a component repaint will be issued
   */
  private void rebuild(boolean forceRepaint) {
    try {
      ThemePreviewBuilder builder = new ThemePreviewBuilder()
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
