// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.welcome.wizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ColorUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class CustomizeUIThemeStepPanel extends JPanel {
  protected static final ThemeInfo DARCULA = new ThemeInfo("Darcula", "Darcula", DarculaLaf.class.getName());
  protected static final ThemeInfo INTELLIJ = new ThemeInfo("Light", "IntelliJ", IntelliJLaf.class.getName());
  private final boolean myColumnMode;
  private final JLabel myPreviewLabel;
  private final Set<ThemeInfo> myThemes = new LinkedHashSet<>();

  protected static final int SMALL_GAP = 10;

  public static Border createSmallEmptyBorder() {
    return BorderFactory.createEmptyBorder(SMALL_GAP, SMALL_GAP, SMALL_GAP, SMALL_GAP);
  }

  public static BorderLayout createSmallBorderLayout() {
    return new BorderLayout(SMALL_GAP, SMALL_GAP);
  }

  @NotNull
  protected static Color getSelectionBackground() {
    return ColorUtil.mix(UIUtil.getListSelectionBackground(true), UIUtil.getLabelBackground(), StartupUiUtil.isUnderDarcula() ? .5 : .75);
  }

  protected static JPanel createBigButtonPanel(LayoutManager layout, final JToggleButton anchorButton, final Runnable action) {
    final JPanel panel = new JPanel(layout) {
      @Override
      public Color getBackground() {
        return anchorButton.isSelected() ? getSelectionBackground() : super.getBackground();
      }
    };
    panel.setOpaque(anchorButton.isSelected());
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        anchorButton.setSelected(true);
        return true;
      }
    }.installOn(panel);
    anchorButton.addItemListener(new ItemListener() {
      boolean curState = anchorButton.isSelected();
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && curState != anchorButton.isSelected()) {
          action.run();
        }
        curState = anchorButton.isSelected();
        panel.setOpaque(curState);
        panel.repaint();
      }
    });
    return panel;
  }

  public CustomizeUIThemeStepPanel() {
    setLayout(createSmallBorderLayout());

    initThemes(myThemes);

    myColumnMode = myThemes.size() > 2;
    JPanel buttonsPanel = new JPanel(new GridLayout(myColumnMode ? myThemes.size() : 1, myColumnMode ? 1 : myThemes.size(), 5, 5));
    ButtonGroup group = new ButtonGroup();
    final ThemeInfo myDefaultTheme = getDefaultTheme();

    for (final ThemeInfo theme : myThemes) {
      @NlsSafe String themName = theme.name;
      final JRadioButton radioButton = new JRadioButton(themName, myDefaultTheme == theme);
      radioButton.setOpaque(false);
      final JPanel panel = createBigButtonPanel(createSmallBorderLayout(), radioButton, () -> {
        applyLaf(theme, this);
        theme.apply();
      });
      panel.setBorder(createSmallEmptyBorder());
      panel.add(radioButton, myColumnMode ? BorderLayout.WEST : BorderLayout.NORTH);
      Icon icon = theme.getIcon();
      int maxThumbnailSize = 400 / myThemes.size();
      final JLabel label = new JLabel(
        myColumnMode ? IconUtil.scale(IconUtil.cropIcon(icon, maxThumbnailSize * 2, maxThumbnailSize * 2), this, .5f) : icon);
      label.setVerticalAlignment(SwingConstants.TOP);
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      panel.add(label, BorderLayout.CENTER);

      group.add(radioButton);
      buttonsPanel.add(panel);
    }
    add(buttonsPanel, BorderLayout.CENTER);
    myPreviewLabel = new JLabel();
    myPreviewLabel.setHorizontalAlignment(myColumnMode ? SwingConstants.LEFT : SwingConstants.CENTER);
    myPreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
    if (myColumnMode) {
      add(buttonsPanel, BorderLayout.WEST);
      JPanel wrapperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      wrapperPanel.add(myPreviewLabel);
      add(wrapperPanel, BorderLayout.CENTER);
    }
    //Static fields initialization. At this point there is no parent window
    applyLaf(myDefaultTheme, this);
    //Actual UI initialization
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> applyLaf(myDefaultTheme, this));
  }

  protected void initThemes(Collection<? super ThemeInfo> result) {
    if (SystemInfo.isMac) {
      result.add(DARCULA);
      result.add(getDefaultLafOnMac());
    }
    else if (SystemInfo.isWindows) {
      result.add(DARCULA);
      result.add(INTELLIJ);
    }
    else {
      result.add(DARCULA);
      result.add(INTELLIJ);
    }
  }

  @NotNull
  private ThemeInfo getDefaultTheme() {
    if (ApplicationManager.getApplication() != null) {
      if (StartupUiUtil.isUnderDarcula()) return DARCULA;
      return INTELLIJ;
    }
    CloudConfigProvider provider = CloudConfigProvider.getProvider();
    if (provider != null) {
      String lafClassName = provider.getLafClassName();
      if (lafClassName != null) {
        ThemeInfo result = ContainerUtil.find(myThemes, theme -> lafClassName.equals(theme.laf));
        if (result != null) {
          return result;
        }
      }
    }
    return myThemes.iterator().next();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.width += 30;
    return size;
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("step.title.ui.themes");
  }

  private void applyLaf(ThemeInfo theme, Component component) {
    UIManager.LookAndFeelInfo info = new UIManager.LookAndFeelInfo(theme.name, theme.laf);
    try {
      boolean wasUnderDarcula = StartupUiUtil.isUnderDarcula();
      UIManager.setLookAndFeel(info.getClassName());
      AppUIUtil.updateForDarcula(StartupUiUtil.isUnderDarcula());
      String className = info.getClassName();
      // TODO: WelcomeWizardUtil.setWizardLAF(className);
      Window window = SwingUtilities.getWindowAncestor(component);
      if (window != null) {
        if (SystemInfo.isMac) {
          window.setBackground(new Color(UIUtil.getPanelBackground().getRGB()));
        }
        SwingUtilities.updateComponentTreeUI(window);
      }
      if (ApplicationManager.getApplication() != null) {
        LafManager lafManager = LafManager.getInstance();
        lafManager.setCurrentLookAndFeel(info);
        lafManager.updateUI();
      }
      if (myColumnMode) {
        myPreviewLabel.setIcon(theme.getIcon());
        myPreviewLabel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Label.foreground")));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @NotNull
  protected static ThemeInfo getDefaultLafOnMac() {
    return INTELLIJ;
  }

  public static class ThemeInfo {
    public final @NonNls String name;
    public final @NonNls String previewFileName;
    public final @NonNls String laf;

    private Icon icon;

    public ThemeInfo(@NonNls String name, @NonNls String previewFileName, @NonNls String laf) {
      this.name = name;
      this.previewFileName = SystemInfo.isMac && "IntelliJ".equals(previewFileName) ? "Aqua" : previewFileName;
      this.laf = laf;
    }

    private Icon getIcon() {
      if (icon == null) {
        String selector;
        if (SystemInfo.isMac) {
          selector = "OSX";
        }
        else if (SystemInfo.isWindows) {
          selector = "Windows";
        }
        else {
          selector = "Linux";
        }
        icon = IconLoader.getIcon("lafs/" + selector + previewFileName + ".png", CustomizeUIThemeStepPanel.class.getClassLoader());
      }
      return icon;
    }

    public void apply() {
    }
  }
}
