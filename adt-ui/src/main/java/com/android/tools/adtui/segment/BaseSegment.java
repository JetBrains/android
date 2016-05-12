package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

public abstract class BaseSegment extends JComponent {

  private static final int SPACER_WIDTH = 100;
  //TODO Adjust this when the vertical label gets integrated.
  private static final int TEXT_FIELD_WIDTH = 50;
  private static final Color BACKGROUND_COLOR = Color.white;
  private final CompoundBorder mCompoundBorder;
  private JPanel mRightPanel;

  @NonNull
  protected final String myName;

  @NonNull
  protected Range mScopedRange;


  public static int getSpacerWidth() {
    return SPACER_WIDTH;
  }

  public static int getTextFieldWidth() {
    return TEXT_FIELD_WIDTH;
  }

  public BaseSegment(@NonNull String name, @NonNull Range scopedRange) {
    myName = name;
    mScopedRange = scopedRange;
    //TODO Adjust borders according to neighbors
    mCompoundBorder = new CompoundBorder(new MatteBorder(1, 1, 1, 1, Color.lightGray),
                                         new EmptyBorder(0, 0, 0, 0));
  }

  public void initializeComponents() {
    setLayout(new BorderLayout());
    JLabel name = new JLabel();
    name.setText(myName);
    name.setBorder(mCompoundBorder);
    this.add(name, BorderLayout.WEST);
    JPanel panels = new JPanel();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1;
    panels.setLayout(new GridBagLayout());

    //Setup the left panel, mostly filled with spacer, or AxisComponent
    JPanel leftPanel = createSpacerPanel();
    gbc.weightx = 0;
    gbc.gridx = 0;
    panels.add(leftPanel, gbc);
    setLeftContent(leftPanel);

    //Setup the center panel, the primary component.
    //This component should consume all available space.
    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BorderLayout());
    centerPanel.setBorder(mCompoundBorder);
    gbc.weightx = 1;
    gbc.gridx = 1;
    panels.add(centerPanel, gbc);
    setCenterContent(centerPanel);

    //Setup the right panel, like the left mostly filled with an AxisComponent
    JPanel rightPanel = createSpacerPanel();
    gbc.weightx = 0;
    gbc.gridx = 2;
    panels.add(rightPanel, gbc);
    setRightContent(rightPanel);

    add(panels, BorderLayout.CENTER);
  }

  private JPanel createSpacerPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBackground(BACKGROUND_COLOR);
    panel.setBorder(mCompoundBorder);
    panel.setPreferredSize(new Dimension(getSpacerWidth(), 0));
    return panel;
  }

  /**
   * This enables segments to toggle the visibilty of the right panel.
   *
   * @param isVisible True indicates the panel is visible, false hides it.
   */
  public void setRightSpacerVisible(boolean isVisible) {
    mRightPanel.setVisible(isVisible);
  }

  protected abstract void setLeftContent(@NonNull JPanel panel);

  protected abstract void setCenterContent(@NonNull JPanel panel);

  protected abstract void setRightContent(@NonNull JPanel panel);

  protected abstract void createComponentsList(@NonNull List<Animatable> animatables);

  //TODO Refactor out of BaseSegment as this is a VisualTest specific function.
  protected abstract void registerComponents(@NonNull List<AnimatedComponent> components);

}
