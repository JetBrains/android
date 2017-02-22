/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering.errors.ui;

import com.android.tools.idea.rendering.HtmlBuilderHelper;
import com.android.utils.HtmlBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.AlarmFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.intellij.openapi.actionSystem.ActionToolbar.NOWRAP_LAYOUT_POLICY;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;

/**
 * Panel that displays a list of rendering errors.
 *
 * {@see RenderErrorModel}
 */
public class RenderErrorPanel extends JPanel implements ListDataListener, Disposable {
  private static final String TITLE = "Render errors";
  private static final String PROPERTY_MINIMIZED = RenderErrorPanel.class.getCanonicalName() + ".minimized";
  private static final String EMPTY_HTML_BODY = "<html><body></body></html>";
  /**
   * {@link NotificationGroup} used to let the user now that the click on a link did something. This is meant to be used
   * in those actions that do not trigger any UI updates (like Copy stack trace to clipboard).
   */
  private static final NotificationGroup NOTIFICATIONS_GROUP =
    new NotificationGroup("Render error panel notifications", NotificationDisplayType.BALLOON, false);

  @NotNull private final JBList myList;
  @NotNull private final JEditorPane myHtmlDetailPane;
  @NotNull private final JScrollPane myListScrollPane;
  @NotNull private final JBLabel myTitleLabel;
  @NotNull private final JScrollPane myHtmlScrollPane;
  @NotNull private RenderErrorModel myModel;
  private boolean isMinimized;
  @Nullable private MinimizeListener myMinimizeListener;
  /**
   * Whether the user has seen the issues or not. We consider the issues "seen" if the panel is not minimized
   */
  private boolean hasUserSeenNewErrors;

  public RenderErrorPanel() {
    super(new BorderLayout());

    myModel = new RenderErrorModel(Collections.emptyList());

    myHtmlDetailPane = new JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML_BODY);
    myHtmlDetailPane.setEditable(false);
    myHtmlDetailPane.addHyperlinkListener(e -> {
      // TODO: Currently all issues share a common hyperlink listener for now so just get any of them. Different issues
      //       should be able to have different handlers
      HyperlinkListener listener = myModel.getElementAt(0).getHyperlinkListener();
      if (listener != null) {
        listener.hyperlinkUpdate(e);
      }
    });
    myHtmlDetailPane.setContentType(UIUtil.HTML_MIME);
    myHtmlDetailPane.setMargin(JBUI.insets(10));

    myList = new JBList();
    // Set a prototype to allow us do measurements of the list
    //noinspection unchecked
    myList.setPrototypeCellValue(RenderErrorModel.Issue.builder().setSummary("Prototype").build());
    myList.setCellRenderer(new ColoredListCellRenderer<RenderErrorModel.Issue>() {
      @Override
      protected void customizeCellRenderer(JList list,
                                           @NotNull RenderErrorModel.Issue value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        Icon icon = getSeverityIcon(value.getSeverity());
        if (icon != null) {
          setIcon(icon);
        }
        append(value.getSummary());
      }
    });

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.addListSelectionListener(e -> {
      int selectedIndex = myList.getSelectedIndex();

      if (selectedIndex != -1) {
        HtmlBuilder htmlBuilder = new HtmlBuilder();

        RenderErrorModel.Issue selectedIssue = (RenderErrorModel.Issue)myList.getModel().getElementAt(selectedIndex);
        htmlBuilder.addHtml(selectedIssue.getHtmlContent()).newline();

        try {
          myHtmlDetailPane.read(new StringReader(htmlBuilder.getHtml()), null);
          HtmlBuilderHelper.fixFontStyles(myHtmlDetailPane);
          myHtmlDetailPane.setCaretPosition(0);
        }
        catch (IOException e1) {
          setEmptyHtmlDetail();
        }
      }
      else {
        setEmptyHtmlDetail();
      }

      revalidate();
      repaint();
    });

    ActionToolbarImpl toolbar =
      new ActionToolbarImpl(ActionPlaces.UNKNOWN, getActionGroup(), true, false, DataManager.getInstance(), ActionManagerEx.getInstanceEx(),
                            KeymapManagerEx.getInstanceEx());
    toolbar.setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
    toolbar.setBorder(JBUI.Borders.empty());
    Box titlePanel = Box.createHorizontalBox();
    myTitleLabel = new JBLabel(TITLE, SwingConstants.LEFT);

    myTitleLabel.setBorder(JBUI.Borders.empty(0, 5, 0, 20));
    myTitleLabel.setPreferredSize(new Dimension(Short.MAX_VALUE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height));
    myTitleLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          // Double clicking the title bar is the same as clicking the minimize action
          MinimizeAction minimizeAction = new MinimizeAction();
          AnActionEvent event =
            AnActionEvent.createFromAnAction(minimizeAction, e, "unknown", DataManager.getInstance().getDataContext(myTitleLabel));
          ActionUtil.performActionDumbAware(minimizeAction, event);
        }
      }
    });

    titlePanel.add(myTitleLabel);
    titlePanel.add(toolbar.getComponent());
    titlePanel.setMaximumSize(new Dimension(Short.MAX_VALUE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height));

    myListScrollPane = createScrollPane(myList);
    // We display a minimum of 3 issues and a maximum of 5. When there are more than 5 issues, a scrollbar is displayed
    myListScrollPane.setMinimumSize(JBUI.size(0, myList.getFixedCellHeight() * 3));
    myListScrollPane.setPreferredSize(JBUI.size(Short.MAX_VALUE, myList.getFixedCellHeight() * 3));
    myListScrollPane.setMaximumSize(JBUI.size(Short.MAX_VALUE, myList.getFixedCellHeight() * 5));
    myListScrollPane.setBorder(JBUI.Borders.empty());

    myHtmlScrollPane = createScrollPane(myHtmlDetailPane);
    myHtmlScrollPane.setBorder(JBUI.Borders.customLine(UIUtil.getPanelBackground(), 5, 0, 0, 0));

    Box rootPanel = Box.createVerticalBox();
    titlePanel.setAlignmentX(CENTER_ALIGNMENT);
    myListScrollPane.setAlignmentX(CENTER_ALIGNMENT);
    myHtmlScrollPane.setAlignmentX(CENTER_ALIGNMENT);
    rootPanel.add(titlePanel);
    rootPanel.add(myListScrollPane);
    rootPanel.add(myHtmlScrollPane);

    add(rootPanel);

    isMinimized = PropertiesComponent.getInstance().getBoolean(PROPERTY_MINIMIZED, false);
    myListScrollPane.setVisible(!isMinimized);
    myHtmlScrollPane.setVisible(!isMinimized);

    updateTitlebarStyle();
  }

  /**
   * Returns the icon associated to the passed {@link HighlightSeverity}
   */
  @Nullable
  private static Icon getSeverityIcon(@NotNull HighlightSeverity severity) {
    if (HighlightSeverity.ERROR.getName().equals(severity.getName())) {
      return AllIcons.General.Error;
    }
    else if (HighlightSeverity.WARNING.getName().equals(severity.getName())) {
      return AllIcons.General.Warning;
    }

    return AllIcons.General.Information;
  }

  public static void showNotification(@NotNull String content) {
    Notification notification = NOTIFICATIONS_GROUP.createNotification(content, NotificationType.INFORMATION);
    Notifications.Bus.notify(notification);

    AlarmFactory.getInstance().create().addRequest(
      notification::expire,
      TimeUnit.SECONDS.toMillis(2)
    );
  }

  @Override
  public void dispose() {
    myModel.removeListDataListener(this);
    myMinimizeListener = null;
  }

  @NotNull
  public RenderErrorModel getModel() {
    return myModel;
  }

  public void setModel(@NotNull RenderErrorModel model) {
    if (model.equals(myModel)) {
      return;
    }
    myModel.removeListDataListener(this);
    // The user has seen the new errors only if the panel is not minimized
    hasUserSeenNewErrors = !isMinimized();
    myModel = model;
    myModel.addListDataListener(this);

    contentsChanged(new ListDataEvent(myModel, ListDataEvent.CONTENTS_CHANGED, 0, myModel.getSize()));
  }

  @NotNull
  private ActionGroup getActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(new MinimizeAction());

    return actionGroup;
  }

  private void setEmptyHtmlDetail() {
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        myHtmlDetailPane.read(new StringReader(EMPTY_HTML_BODY), null);
      }
      catch (IOException ignore) {
      }
    });
  }

  @Override
  public void intervalAdded(ListDataEvent e) {
  }

  @Override
  public void intervalRemoved(ListDataEvent e) {
  }

  @Override
  public void contentsChanged(ListDataEvent e) {
    final List<RenderErrorModel.Issue> orderedIssues = myModel.getIssues().stream().sorted().collect(Collectors.toList());
    UIUtil.invokeLaterIfNeeded(() -> {
      // Try to preserve the selected index. If the current model is empty, just selected the first element of the NEW model.
      int currentSelectedIndex = myList.getItemsCount() == 0 ? 0 : myList.getSelectedIndex();
      //noinspection unchecked
      myList.setModel(new CollectionListModel<>(orderedIssues));
      myList.setSelectedIndex(myModel.getSize() > 0 ? currentSelectedIndex : -1);

      // Try to preserve the current selection
      myList.setSelectedIndex(currentSelectedIndex);

      setVisible(myModel.getSize() > 0);
      updateTitlebarStyle();
      revalidate();
      repaint();
    });
  }

  public boolean isMinimized() {
    return isMinimized;
  }

  public void setMinimized(boolean minimized) {
    if (minimized == isMinimized) {
      return;
    }

    if (!minimized) {
      hasUserSeenNewErrors = true;
    }

    isMinimized = minimized;
    myListScrollPane.setVisible(!isMinimized);
    myHtmlScrollPane.setVisible(!isMinimized);

    updateTitlebarStyle();

    revalidate();
    repaint();

    if (myMinimizeListener != null) {
      UIUtil.invokeLaterIfNeeded(() -> myMinimizeListener.onMinimizeChanged(isMinimized));
    }
  }

  /**
   * Updates the titlebar style depending on the current panel state (whether is minimized or has new elements).
   */
  private void updateTitlebarStyle() {
    // If there are new errors and the panel is minimized, set the title to bold
    myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(!isMinimized() || hasUserSeenNewErrors ? Font.PLAIN : Font.BOLD));

    // When minimized, the title bar displays the icon of the highest severity element in the list of issues and the count of issues
    if (isMinimized) {
      myTitleLabel.setText(String.format("%1$s (%2$d issues)", TITLE, getModel().getSize()));
      myTitleLabel.setIcon(getSeverityIcon(getModel().getHighestSeverity()));
    }
    else {
      myTitleLabel.setText(TITLE);
      myTitleLabel.setIcon(null);
    }
  }

  public void setMinimizeListener(@Nullable MinimizeListener listener) {
    myMinimizeListener = listener;
  }

  @VisibleForTesting
  @NotNull
  public JEditorPane getHtmlDetailPane() {
    return myHtmlDetailPane;
  }

  /**
   * Listener to be notified when this panel is minified
   */
  public interface MinimizeListener {
    void onMinimizeChanged(boolean isMinimized);
  }

  /**
   * Action invoked by the user to minimize or restore the errors panel
   */
  private class MinimizeAction extends AnAction implements RightAlignedToolbarAction {
    private static final String DESCRIPTION = "Minimize/Restore the render errors panel";

    private MinimizeAction() {
      super(DESCRIPTION, DESCRIPTION, AllIcons.General.HideDown);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean newMinimizedState = !isMinimized();

      setMinimized(newMinimizedState);
      // If the user acts on the minimize button, save the preference
      PropertiesComponent.getInstance().setValue(PROPERTY_MINIMIZED, newMinimizedState, false);
      update(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(isMinimized() ? AllIcons.Debugger.RestoreLayout : AllIcons.General.HideDown);
    }
  }
}
