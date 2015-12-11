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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.DeviceInfo;
import com.android.tools.idea.editors.gfxtrace.widgets.TextField;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.*;
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

  private DeviceInfo.Package mySelectedPackage;
  private DeviceInfo.Activity mySelectedActivity;
  private boolean myUserHasChangedTraceName = false;

  @NotNull private Listener myListener = NULL_LISTENER;

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

  public ActivitySelector(DeviceInfo.Provider dip) {
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

    final ListenableFuture<DeviceInfo> future = getDeviceInfo(dip);
    future.addListener(new Runnable() {
      @Override
      public void run() {
        DeviceInfo deviceInfo = null;
        try {
          deviceInfo = future.get();
        }
        catch (Exception e) {
          myStatus.setText("Error: " + e.getMessage());
          return;
        }
        myStatus.setVisible(false);
        myTree.setModel(new DeviceTreeModel(mySearchBox, deviceInfo));
        myTree.setVisible(true);
        pack();
      }
    }, EdtExecutor.INSTANCE);

    myTree.setRowHeight(20);
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
        buttonOK.setEnabled(mySelectedPackage != null && mySelectedActivity != null);
      }
    });

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

  private void createUIComponents() {
    mySearchBox = new RegexFilterComponent(ActivitySelector.class.getName(), 10);
  }

  /**
   * getDeviceInfo returns a {@link ListenableFuture<DeviceInfo>} derived from the
   * {@link DeviceInfo.Provider}. All packages with no activities will be stripped
   * from the resolved {@link DeviceInfo}.
   *
   * @param dip The {@link DeviceInfo} provider.
   */
  private ListenableFuture<DeviceInfo> getDeviceInfo(DeviceInfo.Provider dip) {
    ListenableFuture<DeviceInfo> future = dip.getDeviceInfo(18, 18);

    // Filter out packages with no activities.
    return Futures.transform(future, new Function<DeviceInfo, DeviceInfo>() {
      @Override
      public DeviceInfo apply(DeviceInfo deviceInfo) {
        return deviceInfo.transform(new DeviceInfo.Transform<DeviceInfo.Package>() {
          @Override
          public DeviceInfo.Package transform(DeviceInfo.Package obj) {
            if (obj.myActivities.length == 0) {
              return null; // filter out packages with no activities.
            }
            return obj;
          }
        });
      }
    });
  }

  /**
   * DeviceTreeModel implements a {@link TreeModel} for the given {@link DeviceInfo}.
   */
  private static class DeviceTreeModel implements TreeModel {
    private final RegexFilterComponent myFilter;
    private final DeviceInfo myDevice;
    private DeviceInfo myFilteredDevice;
    private final List<TreeModelListener> listeners = Lists.newArrayList();

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
