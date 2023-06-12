/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions;

import static com.android.tools.profilers.ProfilerColors.HOVERED_SESSION_COLOR;
import static com.android.tools.profilers.ProfilerColors.SELECTED_SESSION_COLOR;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view for showing different {@link SessionArtifact}'s in the sessions panel.
 */
public abstract class SessionArtifactView<T extends SessionArtifact> extends JPanel {

  private static final String PREVIOUS = "Previous";
  private static final String NEXT = "Next";
  private static final String SELECT = "Select";

  private static final Border ARTIFACT_ICON_BORDER = JBUI.Borders.empty(2, 0);
  private static final Border EXPORT_ICON_BORDER = JBUI.Borders.empty(4);
  protected static final Border SELECTED_BORDER = JBUI.Borders.customLine(SELECTED_SESSION_COLOR, 0, 3, 0, 0);
  protected static final Border UNSELECTED_BORDER = JBUI.Borders.emptyLeft(3);

  protected static final Border ARTIFACT_PADDING = JBUI.Borders.empty(3, 9, 3, 4);
  protected static final Border LABEL_PADDING = JBUI.Borders.empty(1, 8, 1, 0);

  protected static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(3);
  protected static final Font STATUS_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(1);

  private static final Icon EXPORT_ICON = StudioIcons.Profiler.Sessions.SAVE;
  private static final Icon EXPORT_ICON_HOVERED = IconUtil.darker(StudioIcons.Profiler.Sessions.SAVE, 3);

  @NotNull private final T myArtifact;
  @NotNull private final ArtifactDrawInfo myArtifactDrawInfo;
  @NotNull protected final AspectObserver myObserver;

  @NotNull private final JComponent myArtifactView;
  @Nullable private JComponent myExportLabel;

  @NotNull private final List<JComponent> myMouseListeningComponents;

  public SessionArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull T artifact) {
    setFocusable(true);
    myArtifactDrawInfo = artifactDrawInfo;
    myArtifact = artifact;
    myObserver = new AspectObserver();
    myMouseListeningComponents = new ArrayList<>();

    JComponent artifactView = buildComponent();
    if (artifact.getCanExport()) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder());
      artifactView.setOpaque(false);
      panel.add(artifactView, BorderLayout.CENTER);
      myExportLabel = buildExportButton();
      myExportLabel.setVisible(false);
      addMouseListeningComponents(myExportLabel);
      panel.add(myExportLabel, BorderLayout.EAST);
      myArtifactView = panel;
    }
    else {
      myArtifactView = artifactView;
    }
    myArtifactView.setBackground(HOVERED_SESSION_COLOR);
    myArtifactView.setOpaque(false);
    setLayout(new BorderLayout());
    add(myArtifactView, BorderLayout.CENTER);

    installContextMenus(this);
    addMouseListeningComponents(this);
    initializeMouseListeners();

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (!GraphicsEnvironment.isHeadless()) {
          // When a component is first constructed, we need to check whether the mouse is already hovered. If so, draw the hover effect.
          PointerInfo info = MouseInfo.getPointerInfo();
          if (info != null) {
            Point mousePosition = info.getLocation();
            SwingUtilities.convertPointFromScreen(mousePosition, SessionArtifactView.this);
            showHoverState(SessionArtifactView.this.contains(mousePosition));
          }
        }
      }
    });

    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        showHoverState(true);
      }

      @Override
      public void focusLost(FocusEvent e) {
        showHoverState(false);
      }
    });

    InputMap inputMap = getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), NEXT);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), PREVIOUS);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), SELECT);
    ActionMap actionMap = getActionMap();
    actionMap.put(NEXT,new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int artifactCount = myArtifactDrawInfo.mySessionsView.getSessionsPanel().getComponentCount();
        int nextIndex = Math.floorMod(myArtifactDrawInfo.myIndex + 1, artifactCount);
        myArtifactDrawInfo.mySessionsView.getSessionsPanel().getComponent(nextIndex).requestFocusInWindow();
      }
    });
    actionMap.put(PREVIOUS,new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int artifactCount = myArtifactDrawInfo.mySessionsView.getSessionsPanel().getComponentCount();
        int prevIndex = Math.floorMod(myArtifactDrawInfo.myIndex - 1, artifactCount);
        myArtifactDrawInfo.mySessionsView.getSessionsPanel().getComponent(prevIndex).requestFocusInWindow();
      }
    });
    actionMap.put(SELECT,new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getArtifact().onSelect();
      }
    });
  }

  /**
   * Helper method for installing the context menus on the input component.
   */
  protected void installContextMenus(@NotNull JComponent component) {
    ContextMenuInstaller contextMenuInstaller = getSessionsView().getIdeProfilerComponents().createContextMenuInstaller();
    getContextMenus().forEach(menu -> contextMenuInstaller.installGenericContextMenu(component, menu));
  }

  /**
   * Adding other mouse listeners (e.g. setting tooltips) in children components within the SessionArtifactView will prevent mouse events
   * to propagate to the view itself for handling common logic such as selection and hover. Here we can add those children components to
   * also handle the common logic on their own.
   */
  protected void addMouseListeningComponents(@NotNull JComponent comp) {
    myMouseListeningComponents.add(comp);
  }

  private void initializeMouseListeners() {
    for (JComponent comp : myMouseListeningComponents) {
      // Mouse listener to handle selection and hover effects.
      comp.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e) && !e.isConsumed()) {
            myArtifact.onSelect();
          }
          requestFocusInWindow();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          showHoverState(true);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          showHoverState(false);
        }
      });
    }
  }

  @NotNull
  public SessionsView getSessionsView() {
    return myArtifactDrawInfo.mySessionsView;
  }

  @Nullable
  @VisibleForTesting
  public JComponent getExportLabel() {
    return myExportLabel;
  }

  @NotNull
  public T getArtifact() {
    return myArtifact;
  }

  @NotNull
  public StudioProfilers getProfilers() {
    return myArtifactDrawInfo.mySessionsView.getProfilers();
  }

  public boolean isSessionSelected() {
    return myArtifact.getSession().equals(myArtifact.getProfilers().getSessionsManager().getSelectedSession());
  }

  public int getIndex() {
    return myArtifactDrawInfo.myIndex;
  }

  @NotNull
  protected List<ContextMenuItem> getContextMenus() {
    List<ContextMenuItem> menus = new ArrayList<>();
    if (getArtifact().getCanExport()) {
      DefaultContextMenuItem action = new DefaultContextMenuItem.Builder("Export...")
        .setEnableBooleanSupplier(() -> getArtifact().getCanExport() && !getArtifact().isOngoing())
        .setActionRunnable(() -> exportArtifact())
        .build();
      menus.add(action);
    }
    return menus;
  }

  @NotNull
  protected abstract JComponent buildComponent();

  protected void exportArtifact() {
  }

  private void showHoverState(boolean hover) {
    if (myExportLabel != null) {
      myExportLabel.setVisible(hover);
    }
    myArtifactView.setOpaque(hover);
    myArtifactView.repaint();
  }

  /**
   * Helper method to generate a standard view to display a session's capture artifact.
   */
  @NotNull
  protected JComponent buildCaptureArtifactView(@NotNull String name,
                                                @NotNull String subtitle,
                                                @NotNull Icon icon,
                                                boolean isOngoing) {
    // 1st column for artifact's icon, 2nd column for texts
    // 1st row for showing name, 2nd row for time.
    JPanel panel = new JPanel(new TabularLayout("Fit,*", "Fit,Fit"));

    JPanel iconPanel = new JPanel();
    iconPanel.setLayout(new BoxLayout(iconPanel, BoxLayout.Y_AXIS));
    iconPanel.setOpaque(false);
    JComponent iconLabel = isOngoing ? new AsyncProcessIcon("") : new JLabel(icon);
    iconLabel.setBorder(ARTIFACT_ICON_BORDER);
    iconPanel.add(iconLabel);
    iconPanel.add(Box.createHorizontalGlue());
    panel.add(iconPanel, new TabularLayout.Constraint(0, 0, 2, 1));

    JLabel artifactName = new JLabel(name);
    artifactName.setBorder(LABEL_PADDING);
    artifactName.setFont(TITLE_FONT);
    artifactName.setForeground(StandardColors.TEXT_COLOR);

    JLabel artifactTime = new JLabel(subtitle);
    artifactTime.setBorder(LABEL_PADDING);
    artifactTime.setFont(STATUS_FONT);
    artifactTime
      .setForeground(AdtUiUtils.overlayColor(artifactTime.getBackground().getRGB(), StandardColors.TEXT_COLOR.getRGB(), 0.6f));
    panel.add(artifactName, new TabularLayout.Constraint(0, 1));
    panel.add(artifactTime, new TabularLayout.Constraint(1, 1));

    // Listen to selected session changed so we can update the selection visuals accordingly.
    final Border selectedBorder = BorderFactory.createCompoundBorder(SELECTED_BORDER, ARTIFACT_PADDING);
    final Border unSelectedBorder = BorderFactory.createCompoundBorder(UNSELECTED_BORDER, ARTIFACT_PADDING);
    getProfilers().getSessionsManager().addDependency(myObserver).onChange(SessionAspect.SELECTED_SESSION, () ->
      panel.setBorder(isSessionSelected() ? selectedBorder : unSelectedBorder)
    );
    panel.setBorder(isSessionSelected() ? selectedBorder : unSelectedBorder);

    return panel;
  }

  @NotNull
  private JComponent buildExportButton() {
    JLabel export = new JLabel(EXPORT_ICON);
    export.setBorder(EXPORT_ICON_BORDER);
    export.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          exportArtifact();
          e.consume();
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        export.setIcon(EXPORT_ICON_HOVERED);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        export.setIcon(EXPORT_ICON);
      }
    });
    export.setToolTipText("Export " + getArtifact().getName());
    export.setVerticalAlignment(SwingConstants.CENTER);
    return export;
  }

  /**
   * Helper object to wrap information related to the states of the cell in which a {@link SessionArtifactView} belongs.
   */
  public static class ArtifactDrawInfo {
    @NotNull final SessionsView mySessionsView;
    final int myIndex;

    ArtifactDrawInfo(@NotNull SessionsView sessionsView, int index) {
      mySessionsView = sessionsView;
      myIndex = index;
    }
  }
}
