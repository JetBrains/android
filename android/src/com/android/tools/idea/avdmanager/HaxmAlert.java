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

import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.NONE;
import static com.android.tools.idea.avdmanager.AvdWizardUtils.TAGS_WITH_GOOGLE_API;

/**
 * Component for displaying an alert on the installation state of HAXM/KVM.
 */
public class HaxmAlert extends JPanel {
  private JBLabel myWarningMessage;
  private HyperlinkLabel myErrorInstructionsLink;
  private HyperlinkListener myErrorLinkListener;
  private SystemImageDescription myImageDescription;
  private AccelerationErrorCode myAccelerationErrorCode;

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
      hasLink = true;
      warningTextBuilder.append(accelerationError.getProblem());
      warningTextBuilder.append("<br>");
      myErrorInstructionsLink.setHyperlinkText(accelerationError.getSolution().getDescription());
      if (myErrorLinkListener != null) {
        myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
      }
      Runnable refresh = new Runnable() {
        @Override
        public void run() {
          refresh();
        }
      };
      final Runnable action = AccelerationErrorSolution.getActionForFix(accelerationError, null, refresh, null);
      myErrorLinkListener =
        new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            action.run();
          }
        };
      myErrorInstructionsLink.addHyperlinkListener(myErrorLinkListener);
      myErrorInstructionsLink.setToolTipText(accelerationError.getSolution() != NONE ? accelerationError.getSolutionMessage() : null);
    }

    if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
      if (warningTextBuilder.length() > 0) {
        warningTextBuilder.append("<br>");
      }
      warningTextBuilder.append("This API Level is Deprecated<br>");
    }

    Abi abi = Abi.getEnum(myImageDescription.getAbiType());
    if (abi != Abi.X86 && abi != Abi.X86_64) {
      if (warningTextBuilder.length() > 0) {
        warningTextBuilder.append("<br>");
      }
      warningTextBuilder.append("Consider using an x86 system image on a x86 host for better emulation performance.<br>");
    }

    if (!TAGS_WITH_GOOGLE_API.contains(myImageDescription.getTag())) {
      if (warningTextBuilder.length() > 0) {
        warningTextBuilder.append("<br>");
      }
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

  @NotNull
  public AccelerationErrorCode getAccelerationState(boolean forceRefresh) {
    if (myAccelerationErrorCode == null || forceRefresh) {
      myAccelerationErrorCode = computeAccelerationState();
    }
    return myAccelerationErrorCode;
  }

  private static AccelerationErrorCode computeAccelerationState() {
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    return manager.checkAcceration();
  }
}
