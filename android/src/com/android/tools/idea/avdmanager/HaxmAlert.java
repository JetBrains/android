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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;

/**
 * Component for displaying an alert on the installation state of HAXM/KVM.
 */
public class HaxmAlert extends JPanel {
  private JBLabel myWarningMessage;
  private HyperlinkLabel myErrorInstructionsLink;
  private HyperlinkListener myErrorLinkListener;
  SystemImageDescription myImageDescription;

  private static AccelerationErrorCode ourAccelerationError = null;

  private static final IdDisplay GOOGLE_APIS_TAG = new IdDisplay("google_apis", "");

  public HaxmAlert() {
    myErrorInstructionsLink = new HyperlinkLabel();
    myWarningMessage = new JBLabel() {
      @Override
      public Dimension getPreferredSize() {
        // Since this contains auto-wrapped text, the preferred height will not be set until repaint(). The below will set it as soon
        // as the actual width is known. This allows the wizard dialog to be set to the correct size even before this step is shown.
        final View view = (View)getClientProperty("html");
        Component parent = getParent();
        if (view != null && parent != null && parent.getWidth() > 0 && parent.getWidth() != (int)view.getPreferredSpan(View.X_AXIS)) {
          view.setSize(parent.getWidth(), 0);
          return new Dimension((int)view.getPreferredSpan(View.X_AXIS), (int)view.getPreferredSpan(View.Y_AXIS));
        }
        return super.getPreferredSize();
      }
    };
    this.setLayout(new GridLayoutManager(2, 1));
    GridConstraints constraints = new GridConstraints();
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);
    add(myWarningMessage, constraints);
    constraints.setRow(1);
    add(myErrorInstructionsLink, constraints);
    myErrorInstructionsLink.setOpaque(false);
    myWarningMessage.setForeground(JBColor.RED);
    myWarningMessage.setHorizontalAlignment(SwingConstants.LEFT);
    setOpaque(false);
    this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Recommendation"),
                                                      BorderFactory.createEmptyBorder(0, 5, 3, 5)));
  }

  public void setSystemImageDescription(SystemImageDescription description) {
    myImageDescription = description;
    refresh();
  }

  private void refresh() {
    if (myImageDescription == null) {
      setVisible(false);
      return;
    }
    boolean hasLink = false;
    StringBuilder warningTextBuilder = new StringBuilder();

    AccelerationErrorCode accelerationError = getAccelerationState(false);
    if (accelerationError != AccelerationErrorCode.ALREADY_INSTALLED) {
      switch (accelerationError.getSolution()) {
        case INSTALL_HAXM:
        case REINSTALL_HAXM:
          hasLink = true;
          setupDownloadLink();
          warningTextBuilder.append(accelerationError.getSolutionMessage()).append("\n");
          break;
        default:
          warningTextBuilder.append(accelerationError.getProblem()).append("\n");
          warningTextBuilder.append(accelerationError.getSolutionMessage()).append("\n");
      }
    }

    if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
      warningTextBuilder.append("This API Level is Deprecated<br>");
    }

    if (!GOOGLE_APIS_TAG.equals(myImageDescription.getTag())) {
      warningTextBuilder.append("Consider using a system image with Google APIs to enable testing with Google Play Services.");
    }

    String warningText = warningTextBuilder.toString();
    if (!warningText.isEmpty()) {
      warningTextBuilder.insert(0, "<html>");
      warningTextBuilder.append("</html>");
      myWarningMessage.setText(warningTextBuilder.toString().replaceAll("\n", "<br>"));
      setVisible(true);
      myErrorInstructionsLink.setVisible(hasLink);
    } else {
      setVisible(false);
    }
  }

  private void setupDownloadLink() {
    if (SystemInfo.isLinux) {
      setupDownloadLinkForLinux();
    }
    else {
      setupDownloadLinkForWindowsAndMac();
    }
  }

  private void setupDownloadLinkForWindowsAndMac() {
    myErrorInstructionsLink.setHyperlinkTarget(null);
    myErrorInstructionsLink.setHtmlText("<a>Download and install HAXM<a>");
    if (myErrorLinkListener != null) {
      myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
    }
    myErrorLinkListener =
      new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          HaxmWizard wizard = new HaxmWizard();
          wizard.init();
          wizard.show();
          getAccelerationState(true);
          refresh();
        }
      };
    myErrorInstructionsLink.addHyperlinkListener(myErrorLinkListener);
  }

  private void setupDownloadLinkForLinux() {
    myErrorInstructionsLink.setHyperlinkTarget(FirstRunWizardDefaults.KVM_LINUX_INSTALL_URL);
    myErrorInstructionsLink.setHtmlText("<a>KVM Instructions</a>");
    if (myErrorLinkListener != null) {
      myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
    }
    myErrorLinkListener =
      new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          // Invalidate the current cached acceleration status:
          ourAccelerationError = null;
        }
      };
    myErrorInstructionsLink.addHyperlinkListener(myErrorLinkListener);
  }

  @NotNull
  public static AccelerationErrorCode getAccelerationState(boolean forceRefresh) {
    if (ourAccelerationError == null || forceRefresh) {
      ourAccelerationError = computeAccelerationState();
    }
    return ourAccelerationError;
  }

  private static AccelerationErrorCode computeAccelerationState() {
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    return manager.checkAcceration();
  }
}
