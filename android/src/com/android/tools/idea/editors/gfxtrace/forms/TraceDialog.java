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
package com.android.tools.idea.editors.gfxtrace.forms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;

/**
 * TraceDialog is a {@link JDialog} that presents options for starting a new graphics trace.
 */
public class TraceDialog extends JDialog {
  private JPanel contentPane;
  private JButton myBeginButton;
  private JButton myCancelButton;
  private JTextField myTraceName;
  private JProgressBar myProgressBar;
  private JPanel myProgressPanel;
  private JLabel myActionLabel;
  private JLabel myActionDetails;
  private State myState = State.UNINITIALIZED;

  private enum State {
    UNINITIALIZED,
    PRE_TRACE,
    TRACING,
    ERROR,
  }

  @NotNull private Listener myListener = NULL_LISTENER;

  private static final Listener NULL_LISTENER = new Listener() {
    @Override
    public void onStartTrace(@NotNull String name) {
    }

    @Override
    public void onStopTrace() {
    }

    @Override
    public void onCancelTrace() {
    }
  };

  public interface Listener {
    void onStartTrace(@NotNull String name);

    void onStopTrace();

    void onCancelTrace();
  }

  public TraceDialog() {
    setTitle("Graphics Trace");
    setContentPane(contentPane);
    getRootPane().setDefaultButton(myBeginButton);

    myBeginButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onBegin();
      }
    });

    // call cancelStopOrClose() when the cancel / stop / close button is clicked
    myCancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelStopOrClose();
      }
    });

    // call cancelStopOrClose() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cancelStopOrClose();
      }
    });

    // call cancelStopOrClose() on ESCAPE
    contentPane.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelStopOrClose();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    setState(State.PRE_TRACE);
    pack();
  }

  public void setListener(@Nullable Listener listener) {
    if (listener != null) {
      myListener = listener;
    }
    else {
      myListener = NULL_LISTENER;
    }
  }

  public void setDefaultName(@NotNull String name) {
    myTraceName.setText(name);
  }

  public void onProgress(@NotNull String action, @NotNull String details) {
    myActionLabel.setText(action);
    myActionDetails.setText(details);
  }

  public void onError(@NotNull String message) {
    myActionLabel.setText(message);
    setState(State.ERROR);
  }

  public void onCancel() {
    dispose();
  }

  public void onStop() {
    dispose();
  }

  private void setState(State state) {
    if (myState == state) {
      return;
    }
    switch (state) {
      case PRE_TRACE:
        myProgressPanel.setVisible(false);
        myTraceName.setEditable(true);
        myBeginButton.setVisible(true);
        myCancelButton.setText("Cancel");
        break;
      case TRACING:
        myProgressPanel.setVisible(true);
        myProgressBar.setVisible(true);
        myTraceName.setEditable(false);
        myBeginButton.setVisible(false);
        myCancelButton.setText("Stop");
        break;
      case ERROR:
        myProgressPanel.setVisible(true);
        myProgressBar.setVisible(false);
        myTraceName.setEditable(false);
        myBeginButton.setVisible(false);
        myCancelButton.setText("Close");
        break;
    }
    myState = state;
    pack();
  }

  public void onBegin() {
    setState(State.TRACING);
    myListener.onStartTrace(myTraceName.getText().trim());
  }

  private void cancelStopOrClose() {
    switch (myState) {
      case PRE_TRACE:
        myListener.onCancelTrace();
        onCancel();
        break;
      case TRACING:
        myListener.onStopTrace();
        onStop();
        break;
      case ERROR:
        onClose();
        break;
    }
  }

  private void onClose() {
    dispose();
  }
}
