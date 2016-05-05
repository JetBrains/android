package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

public abstract class SegmentBase extends JComponent {

  private static final int SPACER_WIDTH = 100;
  private static final Color BACKGROUND_COLOR = Color.white;
  private final CompoundBorder mCompoundBorder;

  @NonNull
  protected final String myName;

  @NonNull
  protected Range mScopedRange;


  public static int getSpacerWidth() {
    return SPACER_WIDTH;
  }

  public SegmentBase(@NonNull String name, @NonNull Range scopedRange) {
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
    JPanel leftPanel = new JPanel();
    leftPanel.setLayout(new BorderLayout());
    leftPanel.setBackground(BACKGROUND_COLOR);
    leftPanel.setBorder(mCompoundBorder);
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
    JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BorderLayout());
    rightPanel.setBackground(BACKGROUND_COLOR);
    rightPanel.setBorder(mCompoundBorder);
    gbc.weightx = 0;
    gbc.gridx = 2;
    panels.add(rightPanel, gbc);
    setRightContent(rightPanel);

    add(panels, BorderLayout.CENTER);
  }

  protected abstract void setLeftContent(@NonNull JPanel panel);

  protected abstract void setCenterContent(@NonNull JPanel panel);

  protected abstract void setRightContent(@NonNull JPanel panel);

  protected abstract void createComponentsList(@NonNull List<Animatable> animatables);

  //TODO Refactor out of SegmentBase as this is a VisualTest specific function.
  protected abstract void registerComponents(@NonNull List<AnimatedComponent> components);

}
