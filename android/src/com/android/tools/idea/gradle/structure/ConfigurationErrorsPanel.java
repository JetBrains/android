/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.android.tools.idea.gradle.structure;

import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Collection;
import java.util.List;

class ConfigurationErrorsPanel extends JPanel implements Disposable, ListDataListener {
  private static final int MAX_ERRORS_TO_SHOW = SystemProperties.getIntProperty("idea.project.structure.max.errors.to.show", 100);

  @NonNls private static final String FIX_ACTION_NAME = "FIX";
  @NonNls private static final String NAVIGATE_ACTION_NAME = "NAVIGATE";

  private ConfigurationErrorsListModel myListModel;
  private ErrorView myCurrentView;

  ConfigurationErrorsPanel() {
    setLayout(new BorderLayout());
    myListModel = new ConfigurationErrorsListModel();
    myListModel.addListDataListener(this);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        revalidate();
        repaint();
      }
    });

    ensureCurrentViewIs(ViewType.ONE_LINE);
  }

  @Override
  public void dispose() {
    if (myListModel != null) {
      myListModel.removeListDataListener(this);
      myListModel = null;
    }
  }

  private void ensureCurrentViewIs(ViewType viewType) {
    if (viewType == ViewType.ONE_LINE) {
      if (myCurrentView instanceof OneLineErrorComponent) {
        return;
      }
      OneLineErrorComponent c = new OneLineErrorComponent(myListModel) {
        @Override
        public void onViewChange(Object data) {
          ensureCurrentViewIs(ViewType.MULTI_LINE);
        }
      };

      if (myCurrentView != null) {
        remove(myCurrentView.self());
        Disposer.dispose(myCurrentView);
      }

      myCurrentView = c;
    }
    else {
      if (myCurrentView instanceof MultiLineErrorComponent) {
        return;
      }
      MultiLineErrorComponent c = new MultiLineErrorComponent(myListModel) {
        @Override
        public void onViewChange(Object data) {
          ensureCurrentViewIs(ViewType.ONE_LINE);
        }
      };

      if (myCurrentView != null) {
        remove(myCurrentView.self());
        Disposer.dispose(myCurrentView);
      }

      myCurrentView = c;
    }

    add(myCurrentView.self(), BorderLayout.CENTER);
    myCurrentView.updateView();

    UIUtil.adjustWindowToMinimumSize(SwingUtilities.getWindowAncestor(this));
    revalidate();
    repaint();
  }

  @Override
  public void intervalAdded(ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void intervalRemoved(ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void contentsChanged(ListDataEvent e) {
    updateCurrentView();
  }

  private void updateCurrentView() {
    if (myCurrentView instanceof MultiLineErrorComponent && myListModel.getSize() == 0) {
      ensureCurrentViewIs(ViewType.ONE_LINE);
    }
    myCurrentView.updateView();
  }

  void addErrors(@NotNull Collection<ProjectConfigurationError> errors) {
    if (myListModel != null) {
      myListModel.addErrors(errors);
    }
  }

  void removeAllErrors() {
    if (myListModel != null) {
      myListModel.removeErrors();
    }
  }

  boolean hasCriticalErrors() {
    List<ProjectConfigurationError> errors = myListModel.getErrors();
    if (!errors.isEmpty()) {
      for (ProjectConfigurationError error : errors) {
        if (!error.isIgnored()) {
          return true;
        }
      }
    }
    return false;
  }

  private interface ErrorView extends Disposable {
    void updateView();

    void onViewChange(@Nullable Object data);

    @NotNull
    JComponent self();
  }

  private abstract static class MultiLineErrorComponent extends JPanel implements ErrorView {
    private JList myList = new JBList();

    protected MultiLineErrorComponent(@NotNull ConfigurationErrorsListModel model) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      myList.setModel(model);
      myList.setCellRenderer(new ErrorListRenderer(myList));
      myList.setBackground(UIUtil.getPanelBackground());

      myList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processListMouseEvent(e, true);
          }
        }
      });

      myList.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processListMouseEvent(e, false);
          }
        }
      });

      myList.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myList.setCellRenderer(new ErrorListRenderer(myList)); // request cell renderer size invalidation
          updatePreferredSize();
        }
      });

      add(new JBScrollPane(myList), BorderLayout.CENTER);
      add(buildToolbar(), BorderLayout.WEST);
    }

    @Override
    public void dispose() {
    }

    private void processListMouseEvent(@NotNull MouseEvent e, boolean wasClicked) {
      int index = myList.locationToIndex(e.getPoint());
      if (index > -1) {
        Object value = myList.getModel().getElementAt(index);
        if (value instanceof ConfigurationError) {
          ConfigurationError error = (ConfigurationError)value;
          Component renderer = myList.getCellRenderer().getListCellRendererComponent(myList, value, index, false, false);
          if (renderer instanceof ErrorListRenderer) {
            Rectangle bounds = myList.getCellBounds(index, index);
            renderer.setBounds(bounds);
            renderer.doLayout();

            Point point = e.getPoint();
            point.translate(-bounds.x, -bounds.y);

            Component deepestComponentAt = SwingUtilities.getDeepestComponentAt(renderer, point.x, point.y);
            if (deepestComponentAt instanceof ToolbarAlikeButton) {
              String name = ((ToolbarAlikeButton)deepestComponentAt).getButtonName();
              if (wasClicked) {
                if (FIX_ACTION_NAME.equals(name)) {
                  onClickFix(error, (JComponent)deepestComponentAt, e);
                }
                else if (NAVIGATE_ACTION_NAME.equals(name)) {
                  error.navigate();
                }
              }
              else {
                String toolTip = "";
                if (FIX_ACTION_NAME.equals(name)) {
                  toolTip = "Fix";
                }
                else if (NAVIGATE_ACTION_NAME.equals(name)) {
                  toolTip = "Navigate to the problem";
                }
                myList.setToolTipText(toolTip);
                return;
              }
            }
            else if (e.getClickCount() == 2) {
              error.navigate();
            }
          }
        }
      }

      myList.setToolTipText(null);
    }

    private static void onClickFix(@NotNull ConfigurationError error, @NotNull JComponent component, @NotNull MouseEvent e) {
      error.fix(component, new RelativePoint(e));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      updatePreferredSize();
    }

    private void updatePreferredSize() {
      Window window = SwingUtilities.getWindowAncestor(this);
      if (window != null) {
        Dimension preferredSize = getPreferredSize();
        setPreferredSize(new Dimension(preferredSize.width, JBUI.scale(200)));
        setMinimumSize(getPreferredSize());
      }
    }

    private JComponent buildToolbar() {
      JPanel result = new JPanel();
      result.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
      result.setLayout(new BorderLayout());
      result.add(new ToolbarAlikeButton(AllIcons.Actions.Collapseall) {
        {
          setToolTipText("Collapse");
        }

        @Override
        public void onClick(MouseEvent e) {
          onViewChange(null);
        }
      }, BorderLayout.NORTH);

      return result;
    }

    @Override
    public void updateView() {
    }

    @Override
    @NotNull
    public JComponent self() {
      return this;
    }
  }

  private abstract static class ToolbarAlikeButton extends JComponent {
    private BaseButtonBehavior myBehavior;
    private Icon myIcon;
    private String myName;

    private ToolbarAlikeButton(@NotNull Icon icon, @NotNull String name) {
      this(icon);
      myName = name;
    }

    private ToolbarAlikeButton(@NotNull Icon icon) {
      myIcon = icon;

      myBehavior = new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        @Override
        protected void execute(MouseEvent e) {
          onClick(e);
        }
      };

      setOpaque(false);
    }

    public String getButtonName() {
      return myName;
    }

    public void onClick(MouseEvent e) {
    }

    @Override
    public Insets getInsets() {
      return new Insets(2, 2, 2, 2);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = new Dimension(myIcon.getIconWidth(), myIcon.getIconHeight());
      JBInsets.addTo(size, getInsets());
      return size;
    }

    @Override
    public void paint(Graphics g) {
      Rectangle bounds = new Rectangle(getWidth(), getHeight());
      JBInsets.removeFrom(bounds, getInsets());

      bounds.x += (bounds.width - myIcon.getIconWidth()) / 2;
      bounds.y += (bounds.height - myIcon.getIconHeight()) / 2;

      if (myBehavior.isPressedByMouse()) {
        bounds.x++;
        bounds.y++;
      }

      myIcon.paintIcon(this, g, bounds.x, bounds.y);
    }
  }

  private static class ErrorListRenderer extends JComponent implements ListCellRenderer {
    private boolean mySelected;
    private boolean myHasFocus;
    private JTextPane myText;
    private JTextPane myFakeTextPane;
    private JViewport myFakeViewport;
    private JList myList;
    private JPanel myButtonsPanel;
    private JPanel myFixGroup;

    private ErrorListRenderer(@NotNull JList list) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      setOpaque(false);

      myList = list;

      myText = new JTextPane();

      myButtonsPanel = new JPanel(new BorderLayout());
      myButtonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
      myButtonsPanel.setOpaque(false);

      JPanel buttons = new JPanel();
      buttons.setOpaque(false);
      buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

      myButtonsPanel.add(buttons, BorderLayout.NORTH);
      add(myButtonsPanel, BorderLayout.EAST);

      myFixGroup = new JPanel();
      myFixGroup.setOpaque(false);
      myFixGroup.setLayout(new BoxLayout(myFixGroup, BoxLayout.Y_AXIS));

      myFixGroup.add(new ToolbarAlikeButton(AllIcons.Actions.QuickfixBulb, FIX_ACTION_NAME) {
      });
      myFixGroup.add(Box.createHorizontalStrut(3));

      buttons.add(myFixGroup);

      buttons.add(new ToolbarAlikeButton(AllIcons.General.AutoscrollToSource, NAVIGATE_ACTION_NAME) {
      });
      buttons.add(Box.createHorizontalStrut(3));

      myFakeTextPane = new JTextPane();
      myText.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
      myFakeTextPane.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      myText.setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myText.setBackground(UIUtil.TRANSPARENT_COLOR);
      }

      myText.setEditable(false);
      myFakeTextPane.setEditable(false);
      myText.setEditorKit(UIUtil.getHTMLEditorKit());
      myFakeTextPane.setEditorKit(UIUtil.getHTMLEditorKit());

      myFakeViewport = new JViewport();
      myFakeViewport.setView(myFakeTextPane);

      add(myText, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      Container parent = myList.getParent();
      if (parent != null) {
        myFakeTextPane.setText(myText.getText());
        Dimension size = parent.getSize();
        myFakeViewport.setSize(size);
        Dimension preferredSize = myFakeTextPane.getPreferredSize();

        Dimension buttonsPrefSize = myButtonsPanel.getPreferredSize();
        int maxHeight = Math.max(buttonsPrefSize.height, preferredSize.height);

        Insets insets = getInsets();
        return new Dimension(Math.min(size.width - JBUI.scale(20), preferredSize.width), maxHeight + insets.top + insets.bottom);
      }

      return super.getPreferredSize();
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      ConfigurationError error = (ConfigurationError)value;
      myList = list;
      mySelected = isSelected;
      myHasFocus = cellHasFocus;
      myFixGroup.setVisible(error.canBeFixed());
      myText.setText(error.getDescription());
      setBackground(error.isIgnored() ? MessageType.WARNING.getPopupBackground() : MessageType.ERROR.getPopupBackground());
      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      Rectangle bounds = getBounds();
      Insets insets = getInsets();

      GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      Shape shape = new RoundRectangle2D.Double(insets.left, insets.top, bounds.width - 1 - insets.left - insets.right,
                                                bounds.height - 1 - insets.top - insets.bottom, 6, 6);

      if (mySelected) {
        g2d.setColor(UIUtil.getListSelectionBackground());
        g2d.fillRect(0, 0, bounds.width, bounds.height);
      }

      g2d.setColor(JBColor.WHITE);
      g2d.fill(shape);


      Color bgColor = getBackground();

      g2d.setColor(bgColor);
      g2d.fill(shape);

      g2d.setColor(myHasFocus || mySelected ? getBackground().darker().darker() : getBackground().darker());
      g2d.draw(shape);
      cfg.restore();

      super.paintComponent(g);
    }
  }

  private abstract static class OneLineErrorComponent extends JComponent implements ErrorView, LinkListener {
    private LinkLabel myErrorsLabel = new LinkLabel("", null);
    private JLabel mySingleErrorLabel = new JLabel();

    private ConfigurationErrorsListModel myModel;

    private OneLineErrorComponent(@NotNull ConfigurationErrorsListModel model) {
      myModel = model;

      setLayout(new BorderLayout());
      setOpaque(true);

      updateLabel(myErrorsLabel, MessageType.ERROR.getPopupBackground(), this, "Errors");
      updateLabel(mySingleErrorLabel, MessageType.ERROR.getPopupBackground(), null, null);
    }

    @Override
    public void dispose() {
      myModel = null;
    }

    private static void updateLabel(@NotNull JLabel label,
                                    @NotNull Color bgColor,
                                    @Nullable LinkListener listener,
                                    @Nullable Object linkData) {
      label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
      label.setOpaque(true);
      label.setBackground(bgColor);
      if (label instanceof LinkLabel) {
        //noinspection ConstantConditions
        ((LinkLabel)label).setListener(listener, linkData);
      }
    }

    @Override
    public void updateView() {
      if (myModel.getSize() == 0) {
        setBorder(null);
      }
      else {
        if (getBorder() == null) {
          setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(5, 0, 5, 0, UIUtil.getPanelBackground()),
                                                       BorderFactory.createLineBorder(UIUtil.getPanelBackground().darker()))
          );
        }
      }

      List<ProjectConfigurationError> errors = myModel.getErrors();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          mySingleErrorLabel.setText(myModel.getErrors().get(0).getPlainTextTitle());
        }
        else {
          myErrorsLabel.setText(String.format("%s errors found", getErrorsCount(errors.size())));
        }
      }

      removeAll();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          add(wrapLabel(mySingleErrorLabel, errors.get(0)), BorderLayout.CENTER);
          mySingleErrorLabel.setToolTipText(errors.get(0).getDescription());
        }
        else {
          add(myErrorsLabel, BorderLayout.CENTER);
        }
      }

      revalidate();
      repaint();
    }

    private static String getErrorsCount(int size) {
      return size < MAX_ERRORS_TO_SHOW ? String.valueOf(size) : MAX_ERRORS_TO_SHOW + "+";
    }

    private JComponent wrapLabel(@NotNull JLabel label, @NotNull ConfigurationError configurationError) {
      JPanel result = new JPanel(new BorderLayout());
      result.setBackground(label.getBackground());
      result.add(label, BorderLayout.CENTER);

      JPanel buttonsPanel = new JPanel();
      buttonsPanel.setOpaque(false);
      buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));

      if (configurationError.canBeFixed()) {
        buttonsPanel.add(new ToolbarAlikeButton(AllIcons.Actions.QuickfixBulb) {
          {
            setToolTipText("Fix error");
          }

          @Override
          public void onClick(MouseEvent e) {
            Object o = myModel.getElementAt(0);
            if (o instanceof ConfigurationError) {
              ((ConfigurationError)o).fix(this, new RelativePoint(e));
              updateView();
              Container ancestor = SwingUtilities.getAncestorOfClass(ConfigurationErrorsPanel.class, this);
              if (ancestor != null && ancestor instanceof JComponent) {
                ((JComponent)ancestor).revalidate();
                ancestor.repaint();
              }
            }
          }
        });

        buttonsPanel.add(Box.createHorizontalStrut(3));
      }

      buttonsPanel.add(new ToolbarAlikeButton(AllIcons.General.AutoscrollToSource) {
        {
          setToolTipText("Navigate to error");
        }

        @Override
        public void onClick(MouseEvent e) {
          Object o = myModel.getElementAt(0);
          if (o instanceof ConfigurationError) {
            ((ConfigurationError)o).navigate();
          }
        }
      });

      buttonsPanel.add(Box.createHorizontalStrut(3));

      result.add(buttonsPanel, BorderLayout.EAST);
      return result;
    }

    @Override
    @NotNull
    public JComponent self() {
      return this;
    }

    @Override
    public abstract void onViewChange(Object data);

    @Override
    public void linkSelected(LinkLabel aSource, Object data) {
      onViewChange(data);
    }
  }

  private static class ConfigurationErrorsListModel extends AbstractListModel {
    private List<ProjectConfigurationError> myAllErrors = Lists.newArrayList();

    @Override
    public int getSize() {
      return Math.min(myAllErrors.size(), MAX_ERRORS_TO_SHOW);
    }

    @Nullable
    @Override
    public Object getElementAt(int index) {
      return myAllErrors.get(index);
    }

    void addErrors(@NotNull Collection<ProjectConfigurationError> errors) {
      for (ProjectConfigurationError error : errors) {
        addError(error);
      }
    }

    private void addError(@NotNull ProjectConfigurationError error) {
      if (!myAllErrors.contains(error)) {
        myAllErrors.add(error);

        int i = myAllErrors.indexOf(error);
        if (i != -1 && i < MAX_ERRORS_TO_SHOW) {
          fireIntervalAdded(this, i, i);
        }
      }
    }

    @NotNull
    private List<ProjectConfigurationError> getErrors() {
      return myAllErrors;
    }

    public void removeErrors() {
      boolean hadErrors = !myAllErrors.isEmpty();
      myAllErrors.clear();
      if (hadErrors) {
        fireContentsChanged(this, 0, 0);
      }
    }
  }

  private enum ViewType {
    ONE_LINE, MULTI_LINE
  }
}

