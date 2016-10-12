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

import com.android.tools.idea.editors.gfxtrace.DeviceInfo;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.widgets.TextField;
import com.android.tools.idea.editors.gfxtrace.widgets.TreeUtil;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.*;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ActivityLauncher is a selection dialog that lists all the packages installed on
 * an Android device and each of their activities.
 */
public class ActivitySelector extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTree myTree;
  private JBLabel myStatus;
  private RegexFilterComponent mySearchBox;
  private TextField myTraceName;
  private AsyncProcessIcon mySpinner;

  private DeviceInfo.Package mySelectedPackage;
  private DeviceInfo.Activity mySelectedActivity;
  private boolean myUserHasChangedTraceName = false;

  @NotNull private Listener myListener = NULL_LISTENER;

  @NotNull private static final Logger LOG = Logger.getInstance(ActivitySelector.class);

  private static final Listener NULL_LISTENER = new Listener() {
    @Override
    public void OnLaunch(DeviceInfo.Package pkg, DeviceInfo.Activity activity, String name) {
    }

    @Override
    public void OnCancel() {
    }
  };


  public interface Listener {
    void OnLaunch(DeviceInfo.Package pkg, DeviceInfo.Activity activity, String name);

    void OnCancel();
  }

  public ActivitySelector(DeviceInfo.PkgInfoProvider dip) {
    setContentPane(contentPane);
    // setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myListener.OnLaunch(mySelectedPackage, mySelectedActivity, myTraceName.getText().trim());
        dispose();
      }
    });

    buttonCancel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myListener.OnCancel();
        dispose();
      }
    });

    myTraceName.addChangedListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myUserHasChangedTraceName = !myTraceName.getText().isEmpty();
      }
    });

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        myListener.OnCancel();
        dispose();
      }
    });

    contentPane.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myListener.OnCancel();
        dispose();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


    myStatus.setText("Loading...");
    myTree.setVisible(false);
    buttonOK.setEnabled(false);

    myTree.setRowHeight(JBUI.scale(20));
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        Icon icon = null;
        if (value instanceof DeviceInfo.Package) {
          DeviceInfo.Package pkg = (DeviceInfo.Package)value;
          append(pkg.myName);
          icon = pkg.myIcon;
        }
        if (value instanceof DeviceInfo.Activity) {
          DeviceInfo.Activity act = (DeviceInfo.Activity)value;
          if (act.myIsLaunch) {
            append(act.myName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else {
            append(act.myName);
          }
          icon = act.myIcon;
        }
        if (icon != null) {
          setIcon(icon);
        }
        else {
          setIcon(AndroidIcons.Android);
        }
      }
    });
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent ev) {
        mySelectedActivity = null;
        mySelectedPackage = null;
        if (ev.isAddedPath()) {
          for (Object object : myTree.getSelectionPath().getPath()) {
            if (object instanceof DeviceInfo.Package) {
              DeviceInfo.Package pkg = (DeviceInfo.Package)object;
              mySelectedActivity = pkg.launchActivity();
              mySelectedPackage = pkg;
            }
            if (object instanceof DeviceInfo.Activity) {
              mySelectedActivity = (DeviceInfo.Activity)object;
            }
          }
          if (mySelectedPackage != null && !myUserHasChangedTraceName) {
            myTraceName.setText(mySelectedPackage.getDisplayName(), false);
          }
        }
        buttonOK.setEnabled(mySelectedPackage != null && mySelectedActivity != null);
      }
    });

    pack();

    dip.getDeviceInfo(18, 18, new DeviceInfo.Listener() {
      @Override
      public void onDeviceInfoReceived(DeviceInfo deviceInfo) {
        ApplicationManager.getApplication().invokeLater(() -> {
          // Filter out packages with no activities.
          DeviceInfo transformed = deviceInfo.transform(obj -> (obj.myActivities.length != 0) ? obj : null);

          myStatus.setVisible(false);

          if (myTree.getModel() instanceof DeviceTreeModel) {
            DeviceTreeModel model = ((DeviceTreeModel)myTree.getModel());
            TreePath previousSelection = myTree.getSelectionPath();
            Enumeration<TreePath> expanded = myTree.getExpandedDescendants(new TreePath(model.getRoot()));

            model.applyDeviceInfoPreservingIcons(transformed);

            if (previousSelection != null) {
              TreePath newSelection = TreeUtil.getTreePathInTree(previousSelection, myTree);
              if (newSelection != null) {
                myTree.setSelectionPath(newSelection);
              }
            }
            while (expanded.hasMoreElements()) {
              TreePath newPath = TreeUtil.getTreePathInTree(expanded.nextElement(), myTree);
              if (newPath != null) {
                myTree.expandPath(newPath);
              }
            }
            ApplicationManager.getApplication().invokeLater(() -> myTree.repaint());
          }
          else {
            myTree.setModel(new DeviceTreeModel(mySearchBox, transformed));
            myTree.setVisible(true);
            pack();
          }
        });
      }

      @Override
      public void onFinished() {
        ApplicationManager.getApplication().invokeLater(() -> { mySpinner.setVisible(false); });
      }

      @Override
      public void onException(Exception e) {
        ApplicationManager.getApplication().invokeLater(() -> {
          mySpinner.setVisible(false);
          LOG.warn(e);
          if (myStatus.isVisible()) {
            myStatus.setText("Error: " + e.getMessage());
          }
          else {
            Messages.showMessageDialog(
              myTree,
              "Cannot fetch package info\n" + e.getMessage(),
              CommonBundle.getErrorTitle(),
              Messages.getErrorIcon());
          }

        });
      }
    });
  }

  public void setListener(@Nullable Listener listener) {
    if (listener != null) {
      myListener = listener;
    }
    else {
      myListener = NULL_LISTENER;
    }
  }

  private void createUIComponents() {
    mySearchBox = new RegexFilterComponent(ActivitySelector.class.getName(), 10);
    mySpinner = new AsyncProcessIcon("Populating package list");
  }

  /**
   * DeviceTreeModel implements a {@link TreeModel} for the given {@link DeviceInfo}.
   */
  private static class DeviceTreeModel implements TreeModel {
    private final RegexFilterComponent myFilter;
    private DeviceInfo myDevice;
    private DeviceInfo myFilteredDevice;
    private final List<TreeModelListener> listeners = Lists.newArrayList();

    /**
     * Updates the tree model with new package information, preserving existing icons
     * for packages and activities where the new data doesn't provide any.
     */
    public void applyDeviceInfoPreservingIcons(DeviceInfo newDeviceInfo) {
      DeviceInfo previous = myDevice;

      myDevice = newDeviceInfo.transform(pkg -> {
        DeviceInfo.Package previousPkg = previous.getPackage(pkg.myName);
        if (previousPkg != null) {
          pkg = pkg.transform(act -> {
            DeviceInfo.Activity previousAct = previousPkg.getActivity(act.myName);
            if (act.myIcon == null && previousAct != null && previousAct.myIcon != null) {
              act.myIcon = previousAct.myIcon;
            }
            return act;
          });

          if (pkg.myIcon == null && previousPkg.myIcon != null) {
            pkg.myIcon = previousPkg.myIcon;
          }
        }
        return pkg;
      });

      filter();
      fireTreeChanged();
    }

    public DeviceTreeModel(RegexFilterComponent filter, DeviceInfo device) {
      myFilter = filter;
      myDevice = device;
      filter();
      filter.addRegexListener(new RegexFilterComponent.Listener() {
        @Override
        public void filterChanged(RegexFilterComponent filter) {
          filter();
          fireTreeChanged();
        }
      });
    }

    @Override
    public Object getRoot() {
      return myDevice;
    }

    @Override
    public Object getChild(Object o, int i) {
      if (o == myDevice) {
        return myFilteredDevice.myPackages[i];
      }
      return ((DeviceInfo.Package)o).myActivities[i];
    }

    @Override
    public int getChildCount(Object o) {
      if (o == myDevice) {
        return myFilteredDevice.myPackages.length;
      }
      return ((DeviceInfo.Package)o).myActivities.length;
    }

    @Override
    public boolean isLeaf(Object o) {
      return o instanceof DeviceInfo.Activity;
    }

    @Override
    public void valueForPathChanged(TreePath treePath, Object o) {
    }

    @Override
    public int getIndexOfChild(Object o, Object o1) {
      if (o == myDevice) {
        for (int i = 0; i < myFilteredDevice.myPackages.length; i++) {
          if (myFilteredDevice.myPackages[i].equals(o1)) {
            return i;
          }
        }
        return myFilteredDevice.myPackages.length;
      }

      DeviceInfo.Package pkg = (DeviceInfo.Package)o;
      for (int i = 0; i < pkg.myActivities.length; i++) {
        if (pkg.myActivities[i].equals(o1)) {
          return i;
        }
      }

      return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener treeModelListener) {
      synchronized (listeners) {
        listeners.add(treeModelListener);
      }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener treeModelListener) {
      synchronized (listeners) {
        listeners.remove(treeModelListener);
      }
    }

    private void filter() {
      final Pattern pattern = myFilter.getPattern();
      if (pattern == null) {
        myFilteredDevice = myDevice;
        return;
      }

      myFilteredDevice = myDevice.transform(new DeviceInfo.Transform<DeviceInfo.Package>() {
        @Override
        public DeviceInfo.Package transform(DeviceInfo.Package obj) {
          if (pattern.matcher(obj.myName).find()) {
            return obj;
          }
          return null;
        }
      });
    }

    private void fireTreeChanged() {
      List<TreeModelListener> ls;
      synchronized (listeners) {
        ls = Lists.newArrayList(listeners);
      }
      for (TreeModelListener l : ls) {
        l.treeStructureChanged(new TreeModelEvent(this, new Object[] { myDevice }));
      }
    }
  }
}
