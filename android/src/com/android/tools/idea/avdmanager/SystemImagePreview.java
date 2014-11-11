/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.stats.Distribution;
import com.android.tools.idea.stats.DistributionService;
import com.google.common.base.CharMatcher;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.SystemImageDescription;

/**
 * Displays information about a {@link com.android.sdklib.SystemImage}, including its
 * launch graphic, platform and API level, and target CPU architecture.
 */
public class SystemImagePreview extends JPanel {
  private static final Logger LOG = Logger.getInstance(SystemImagePreview.class);
  private static final String NO_SYSTEM_IMAGE_SELECTED = "No System Image Selected";
  private static final int FIGURE_PADDING = 3;
  private SystemImageDescription myImageDescription;
  private Distribution myDistribution;
  private static final int PADDING = 20;

  public SystemImagePreview() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        int standardFontHeight = getGraphics().getFontMetrics(AvdWizardConstants.STANDARD_FONT).getHeight();
        if (myDistribution != null && mouseEvent.getY() > getHeight() - PADDING - standardFontHeight) {
          try {
            Desktop.getDesktop().browse(new URI(myDistribution.getUrl()));
          } catch (URISyntaxException e) {
            LOG.error("Syntax exception in url for distribution " + myDistribution.getVersion().toShortString());
          } catch (IOException e) {
            LOG.error("IOException trying to open url " + myDistribution.getUrl());
          }
        }
      }
    });
  }

  /**
   * Set the image to display.
   */
  public void setImage(@Nullable SystemImageDescription image) {
    if (image == null || !image.isRemote()) {
      myImageDescription = image;
    }
    if (image != null && !image.isRemote()) {
      myDistribution = DistributionService.getInstance().getDistributionForApiLevel(image.getVersion().getApiLevel());
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setColor(JBColor.background());
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setColor(JBColor.foreground());
    g2d.setFont(AvdWizardConstants.STANDARD_FONT);

    if (myImageDescription == null) {
      FontMetrics metrics = g2d.getFontMetrics();
      g2d.drawString(NO_SYSTEM_IMAGE_SELECTED,
                   (getWidth() - metrics.stringWidth(NO_SYSTEM_IMAGE_SELECTED)) / 2,
                   (getHeight() - metrics.getHeight()) / 2 );
      return;
    }

    // Paint the device name
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    FontMetrics metrics = g.getFontMetrics(AvdWizardConstants.TITLE_FONT);
    String codeName = getCodeName(myImageDescription);
    if (codeName != null) {
      g2d.drawString(codeName, PADDING, PADDING + metrics.getHeight() / 2);
      g2d.drawLine(0, 50, getWidth(), 50);
    }

    // Paint our icon
    Icon icon = getIcon(codeName);
    if (icon != null) {
      icon.paintIcon(this, g, FIGURE_PADDING, PADDING + 50);
    }

    // Paint the details.
    int stringHeight = g2d.getFontMetrics(AvdWizardConstants.TITLE_FONT).getHeight();
    int figureHeight = g2d.getFontMetrics(AvdWizardConstants.FIGURE_FONT).getHeight();
    int infoSegmentX = FIGURE_PADDING + PADDING + 128;
    int infoSegmentY = PADDING + 75;

    // Paint the API Level
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("API Level", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.getVersion().getApiString(), infoSegmentX, infoSegmentY);
    infoSegmentY += PADDING;

    // Paint the platform version
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("Android", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.getVersionName(), infoSegmentX, infoSegmentY);

    // Paint the vendor name
    String vendorName;
    String tag = myImageDescription.getTag().getId();
    if (tag.equals("android-wear") || tag.equals("android-tv")) {
      vendorName = "Android";
    } else {
      vendorName = myImageDescription.getVendor();
    }
    if (metrics.stringWidth(vendorName) > 128) {
      // Split into two lines
      Iterable<String> parts = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().split(vendorName);
      String currentLine = "";
      for (String part : parts) {
        if (metrics.stringWidth(currentLine) >= 128) {
          infoSegmentY += stringHeight;
          g2d.drawString(currentLine, infoSegmentX, infoSegmentY);
          currentLine = "";
        }
        currentLine += part + " ";
      }
      if (!currentLine.isEmpty()) {
        infoSegmentY += stringHeight;
        g2d.drawString(currentLine, infoSegmentX, infoSegmentY);
      }
    } else {
      infoSegmentY += stringHeight;
      g2d.drawString(vendorName, infoSegmentX, infoSegmentY);
    }
    infoSegmentY += PADDING;

    // Paint the CPU architecture
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("System Image", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.getAbiType(), infoSegmentX, infoSegmentY);

    // If this API level is deprecated, paint a warning
    if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
      infoSegmentY += stringHeight * 2;
      g2d.setFont(AvdWizardConstants.TITLE_FONT);
      g2d.drawString("This API Level is Deprecated", PADDING, infoSegmentY);
    }


    // If this system image is not x86, paint a warning
    HaxmState haxmState = getHaxmState(false);
    if (haxmState == HaxmState.NOT_INSTALLED && !myImageDescription.getAbiType().startsWith(Abi.X86.toString())) {
      infoSegmentY += stringHeight * 2;
      g2d.setFont(AvdWizardConstants.TITLE_FONT);
      g2d.setColor(JBColor.RED);
      g2d.drawString("Consider using a x86 System Image", PADDING, infoSegmentY);
      infoSegmentY += stringHeight;
      g2d.drawString("for better emulation speed", PADDING, infoSegmentY);
    } else if (haxmState == HaxmState.NOT_LATEST) {
      infoSegmentY += stringHeight * 2;
      g2d.setColor(JBColor.RED);
      g2d.setFont(AvdWizardConstants.TITLE_FONT);
      g2d.drawString("Newer HAXM Version Available", PADDING, infoSegmentY);
      infoSegmentY += stringHeight;
      g2d.drawString("(Use SDK Manager)", PADDING, infoSegmentY);
    }

    if (myDistribution != null) {
      // Paint the help link
      g2d.setFont(AvdWizardConstants.STANDARD_FONT);
      g2d.setColor(JBColor.BLUE);
      g2d.drawString("? - See documentation for Android " + myDistribution.getVersion().toShortString() + " APIs", PADDING,
                     getHeight() - PADDING);
    }
  }

  /**
   * @return the codename for the given System Image's API level
   */
  @Nullable
  public static String getCodeName(@NotNull SystemImageDescription description) {
    return SdkVersionInfo.getCodeName(description.getVersion().getApiLevel());
  }

  /**
   * Get the launch graphic which corresponds with the given codename, or a question mark
   * if we don't have an icon for that codename.
   */
  @Nullable
  public static Icon getIcon(@Nullable String codename) {
    if (codename == null) {
      return null;
    }
    try {
      return IconLoader.getIcon(String.format("/icons/versions/%1$s.png", codename), AndroidIcons.class);
    } catch (RuntimeException e) {
      int size = 128;
      Image image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics g = image.getGraphics();
      GraphicsUtil.setupAntialiasing(g);
      GraphicsUtil.setupAAPainting(g);
      Font f = UIUtil.getLabelFont();
      Font font = new Font(f.getName(), f.getStyle() | Font.BOLD, 100);
      g.setColor(JBColor.background());
      g.fillRect(0, 0, size, size);
      g.setColor(JBColor.foreground());
      g.setFont(font);
      int height = g.getFontMetrics().getHeight();
      int width = g.getFontMetrics().stringWidth("?");
      g.drawString("?", (size - width) / 2, height + (size - height) / 2);
      return new ImageIcon(image);
    }
  }

  enum HaxmState { NOT_INITIALIZED, INSTALLED, NOT_INSTALLED, NOT_LATEST }
  private static HaxmState ourHaxmState = HaxmState.NOT_INITIALIZED;

  private static HaxmState getHaxmState(boolean forceRefresh) {
    if (ourHaxmState == HaxmState.NOT_INITIALIZED || forceRefresh) {
      ourHaxmState = computeHaxmState();
    }
    return ourHaxmState;
  }

  private static HaxmState computeHaxmState() {
      try {
        if (SystemInfo.isMac) {
          @SuppressWarnings("SpellCheckingInspection")
          String output = ExecUtil.execAndReadLine("/usr/sbin/kextstat", "-l", "-b", "com.intel.kext.intelhaxm");
          if (output != null && !output.isEmpty()) {
            Pattern pattern = Pattern.compile("com\\.intel\\.kext\\.intelhaxm( \\((.+)\\))?");
            Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
              if (matcher.groupCount() >= 2) {
                String version = matcher.group(2);
                try {
                  FullRevision revision = FullRevision.parseRevision(version);
                  FullRevision current = new FullRevision(1, 1, 1);
                  if (revision.compareTo(current) < 0) {
                    // We have the new version number, as well as the currently installed
                    // version number here, which we could use to make a better error message.
                    // However, these versions do not correspond to the version number we show
                    // in the SDK manager (e.g. in the SDK version manager we show "5"
                    // and the corresponding kernel stat version number is 1.1.1.
                    return HaxmState.NOT_LATEST;
                  }
                }
                catch (NumberFormatException e) {
                  // Some unexpected new (or old?) format for HAXM versions; ignore since we
                  // can't check whether it is up to date.
                }
              }
              return HaxmState.INSTALLED;
            }
          }
          return HaxmState.NOT_INSTALLED;
        } else if (SystemInfo.isWindows) {
          @SuppressWarnings("SpellCheckingInspection")
          ProcessOutput processOutput = ExecUtil.execAndGetOutput(ImmutableList.of("sc", "query", "intelhaxm"), null);
          return Iterables.all(processOutput.getStdoutLines(), new Predicate<String>() {
            @Override
            public boolean apply(String input) {
              return input == null || !input.contains("does not exist");
            }
          }) ? HaxmState.INSTALLED : HaxmState.NOT_INSTALLED;
        } else if (SystemInfo.isUnix) {
          ProcessOutput processOutput = ExecUtil.execAndGetOutput(ImmutableList.of("kvm-ok"), null);
          return Iterables.any(processOutput.getStdoutLines(), new Predicate<String>() {
            @Override
            public boolean apply(String input) {
              return input != null && input.contains("KVM acceleration can be used");
            }
          }) ? HaxmState.INSTALLED : HaxmState.NOT_INSTALLED;
        } else {
          assert !SystemInfo.isLinux; // should be covered by SystemInfo.isUnix
          return HaxmState.NOT_INSTALLED;
        }
      } catch (ExecutionException e) {
        return HaxmState.NOT_INSTALLED;
      }
    }
}
