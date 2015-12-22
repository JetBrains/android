/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.google.common.collect.Lists;
import com.intellij.execution.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProcessChooserDialog extends DialogWrapper {
  @NonNls private static final String DEBUGGABLE_PROCESS_PROPERTY = "DEBUGGABLE_PROCESS";
  @NonNls private static final String SHOW_ALL_PROCESSES_PROPERTY = "SHOW_ALL_PROCESSES";
  @NonNls private static final String DEBUGGABLE_DEVICE_PROPERTY = "DEBUGGABLE_DEVICE";

  private final Project myProject;
  private JPanel myContentPanel;
  private Tree myProcessTree;
  private JBCheckBox myShowAllProcessesCheckBox;
  private JComboBox myDebuggerTypeCombo;

  private String myLastSelectedDevice;
  private String myLastSelectedProcess;

  private final MergingUpdateQueue myUpdatesQueue;
  private final AndroidDebugBridge.IClientChangeListener myClientChangeListener;
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener;

  protected AndroidProcessChooserDialog(@NotNull Project project) {
    super(project);
    setTitle("Choose Process");

    myProject = project;
    myUpdatesQueue =
      new MergingUpdateQueue("AndroidProcessChooserDialogUpdatingQueue", 500, true, MergingUpdateQueue.ANY_COMPONENT, myProject);

    final String showAllProcessesStr = PropertiesComponent.getInstance(project).getValue(SHOW_ALL_PROCESSES_PROPERTY);
    final boolean showAllProcesses = Boolean.parseBoolean(showAllProcessesStr);
    myShowAllProcessesCheckBox.setSelected(showAllProcesses);

    doUpdateTree(showAllProcesses);

    myClientChangeListener = new AndroidDebugBridge.IClientChangeListener() {
      @Override
      public void clientChanged(Client client, int changeMask) {
        updateTree();
      }
    };
    AndroidDebugBridge.addClientChangeListener(myClientChangeListener);

    myDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(IDevice device) {
        updateTree();
      }

      @Override
      public void deviceDisconnected(IDevice device) {
        updateTree();
      }

      @Override
      public void deviceChanged(IDevice device, int changeMask) {
        updateTree();
      }
    };
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);

    myShowAllProcessesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateTree();
      }
    });

    List<AndroidDebugger> androidDebuggers = Lists.newLinkedList();
    for (AndroidDebugger androidDebugger: AndroidDebugger.EP_NAME.getExtensions()) {
      if (androidDebugger.supportsProject(myProject)) {
        androidDebuggers.add(androidDebugger);
      }
    }

    myDebuggerTypeCombo.setModel(new CollectionComboBoxModel(androidDebuggers));
    myDebuggerTypeCombo.setRenderer(new AndroidDebugger.Renderer());

    myProcessTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        IDevice selectedDevice = getSelectedDevice();
        Client selectedClient = getSelectedClient();

        myLastSelectedDevice = getPersistableName(selectedDevice);
        myLastSelectedProcess = getPersistableName(selectedClient);

        getOKAction().setEnabled(selectedDevice != null && selectedClient != null);
      }
    });
    new TreeSpeedSearch(myProcessTree) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        if (element instanceof TreePath) {
          Object lastComponent = ((TreePath)element).getLastPathComponent();
          if (lastComponent instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode)lastComponent).getUserObject();
            if (userObject instanceof Client) {
              String pkg = ((Client)userObject).getClientData().getClientDescription();
              return pkg != null && pkg.contains(pattern);
            }
          }
        }
        return false;
      }
    };

    myProcessTree.setCellRenderer(new JBDefaultTreeCellRenderer(myProcessTree) {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof IDevice) {
            value = ((IDevice)userObject).getName();
          }
          else if (userObject instanceof Client) {
            final ClientData clientData = ((Client)userObject).getClientData();
            String description = clientData.getClientDescription();
            if (clientData.isValidUserId() && clientData.getUserId() != 0) {
              description += " (user " + Integer.toString(clientData.getUserId()) + ")";
            }
            value = description;
          }
        }

        return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      }

      @Override
      public Icon getLeafIcon() {
        return null;
      }

      @Override
      public Icon getOpenIcon() {
        return null;
      }

      @Override
      public Icon getClosedIcon() {
        return null;
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (isOKActionEnabled()) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myProcessTree);

    myProcessTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    myLastSelectedProcess = properties.getValue(DEBUGGABLE_PROCESS_PROPERTY);
    myLastSelectedDevice = properties.getValue(DEBUGGABLE_DEVICE_PROPERTY);

    init();
  }

  @NotNull
  private static String getPersistableName(@Nullable Client client) {
    return client == null ? "" : client.getClientData().getClientDescription();
  }

  @NotNull
  private static String getPersistableName(@Nullable IDevice device) {
    return device == null ? "" : device.getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProcessTree;
  }

  @Override
  protected void dispose() {
    super.dispose();

    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
    AndroidDebugBridge.removeClientChangeListener(myClientChangeListener);
  }

  private void updateTree() {
    final boolean showAllProcesses = myShowAllProcessesCheckBox.isSelected();

    myUpdatesQueue.queue(new Update(AndroidProcessChooserDialog.this) {
      @Override
      public void run() {
        final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myProject);
        if (debugBridge != null && AdbService.isDdmsCorrupted(debugBridge)) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(myContentPanel, AndroidBundle.message("ddms.corrupted.error"));
              AndroidProcessChooserDialog.this.close(1);
            }
          });
          return;
        }

        doUpdateTree(showAllProcesses);
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void doUpdateTree(boolean showAllProcesses) {
    final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myProject);

    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final DefaultTreeModel model = new DefaultTreeModel(root);

    if (debugBridge == null) {
      myProcessTree.setModel(model);
      return;
    }

    final Set<String> processNames = collectAllProcessNames(myProject);

    TreeNode selectedDeviceNode = null;
    TreeNode selectedClientNode = null;

    Object[] firstTreePath = null;

    final IDevice[] devices = debugBridge.getDevices();
    for (IDevice device : devices) {
      final DefaultMutableTreeNode deviceNode = new DefaultMutableTreeNode(device);
      root.add(deviceNode);

      final String deviceName = device.getName();
      if (deviceName.equals(myLastSelectedDevice)) {
        selectedDeviceNode = deviceNode;
      }

      for (Client client : device.getClients()) {
        final String clientDescription = client.getClientData().getClientDescription();

        if (clientDescription != null && (showAllProcesses || isRelatedProcess(processNames, clientDescription))) {
          final DefaultMutableTreeNode clientNode = new DefaultMutableTreeNode(client);
          deviceNode.add(clientNode);

          if (clientDescription.equals(myLastSelectedProcess) && (selectedDeviceNode == null || deviceName.equals(myLastSelectedDevice))) {
            selectedClientNode = clientNode;
            selectedDeviceNode = deviceNode;
          }

          if (firstTreePath == null) {
            firstTreePath = new Object[]{root, deviceNode, clientNode};
          }
        }
      }
    }

    final Object[] pathToSelect;
    if (selectedDeviceNode != null && selectedClientNode != null) {
      pathToSelect = new Object[]{root, selectedDeviceNode, selectedClientNode};
    }
    else if (selectedDeviceNode != null) {
      pathToSelect = new Object[]{root, selectedDeviceNode};
    }
    else {
      pathToSelect = firstTreePath;
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myProcessTree.setModel(model);

        if (pathToSelect != null) {
          myProcessTree.getSelectionModel().setSelectionPath(new TreePath(pathToSelect));
        }
        else {
          getOKAction().setEnabled(false);
        }

        TreeUtil.expandAll(myProcessTree);
      }
    });
  }

  private static boolean isRelatedProcess(Set<String> processNames, String clientDescription) {
    final String lc = clientDescription.toLowerCase();

    for (String processName : processNames) {
      if (lc.startsWith(processName)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static Set<String> collectAllProcessNames(Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    final Set<String> result = new HashSet<String>();

    for (AndroidFacet facet : facets) {
      final String packageName = AndroidCompileUtil.getAaptManifestPackage(facet);

      if (packageName != null) {
        result.add(packageName.toLowerCase());
      }
      final Manifest manifest = facet.getManifest();

      if (manifest != null) {
        final XmlElement xmlElement = manifest.getXmlElement();

        if (xmlElement != null) {
          collectProcessNames(xmlElement, result);
        }
      }
      final AndroidModel androidModel = facet.getAndroidModel();
      if (androidModel != null) {
        result.addAll(androidModel.getAllApplicationIds());
      }
    }

    return result;
  }

  private static void collectProcessNames(XmlElement xmlElement, final Set<String> result) {
    xmlElement.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        if ("process".equals(attribute.getLocalName())) {
          final String value = attribute.getValue();

          if (value != null) {
            result.add(value.toLowerCase());
          }
        }
      }
    });
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  protected void doOKAction() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final IDevice selectedDevice = getSelectedDevice();
    if (selectedDevice == null) {
      return;
    }

    final Client selectedClient = getSelectedClient();
    if (selectedClient == null) {
      return;
    }

    super.doOKAction();

    properties.setValue(DEBUGGABLE_DEVICE_PROPERTY, getPersistableName(selectedDevice));
    properties.setValue(DEBUGGABLE_PROCESS_PROPERTY, getPersistableName(selectedClient));
    properties.setValue(SHOW_ALL_PROCESSES_PROPERTY, Boolean.toString(myShowAllProcessesCheckBox.isSelected()));

    closeOldSessionAndRun(selectedClient);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidProcessChooserDialog";
  }

  private void closeOldSessionAndRun(@NotNull Client client) {
    // Disconnect any active run sessions to the same client
    terminateRunSessions(client);
    runSession(client);
  }

  private void terminateRunSessions(@NotNull Client selectedClient) {
    int pid = selectedClient.getClientData().getPid();

    // find if there are any active run sessions to the same client, and terminate them if so
    for (ProcessHandler handler : ExecutionManager.getInstance(myProject).getRunningProcesses()) {
      if (handler instanceof AndroidProcessHandler) {
        Client client = ((AndroidProcessHandler)handler).getClient(selectedClient.getDevice());
        if (client != null && client.getClientData().getPid() == pid) {
          ((AndroidProcessHandler)handler).setNoKill();
          handler.detachProcess();
          handler.notifyTextAvailable("Disconnecting run session: a new debug session will be established.\n", ProcessOutputTypes.STDOUT);
          break;
        }
      }
    }
  }

  private void runSession(@NotNull Client client) {
    AndroidDebugger androidDebugger = (AndroidDebugger)myDebuggerTypeCombo.getSelectedItem();
    androidDebugger.attachToClient(myProject, client);
  }

  @Nullable
  private IDevice getSelectedDevice() {
    final TreePath selectionPath = myProcessTree.getSelectionPath();
    if (selectionPath == null || selectionPath.getPathCount() < 2) {
      return null;
    }

    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)selectionPath.getPathComponent(1);
    final Object obj = selectedNode.getUserObject();
    return obj instanceof IDevice ? (IDevice)obj : null;
  }

  @Nullable
  private Client getSelectedClient() {
    final TreePath selectionPath = myProcessTree.getSelectionPath();
    if (selectionPath == null || selectionPath.getPathCount() < 3) {
      return null;
    }

    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)selectionPath.getPathComponent(2);
    final Object obj = selectedNode.getUserObject();
    return obj instanceof Client ? (Client)obj : null;
  }
}
