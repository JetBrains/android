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
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.google.common.collect.Lists;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.*;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AndroidProcessChooserDialog extends DialogWrapper {
  @NonNls private static final String DEBUGGABLE_PROCESS_PROPERTY = "DEBUGGABLE_PROCESS";
  @NonNls private static final String SHOW_ALL_PROCESSES_PROPERTY = "SHOW_ALL_PROCESSES";
  @NonNls private static final String DEBUGGABLE_DEVICE_PROPERTY = "DEBUGGABLE_DEVICE";
  @NonNls private static final String DEBUGGER_ID_PROPERTY = "DEBUGGER_ID";

  private final Project myProject;
  private final boolean myShowDebuggerSelection;
  private final MyProcessTreeCellRenderer myCellRenderer;
  private JPanel myContentPanel;
  private Tree myProcessTree;
  private JBCheckBox myShowAllProcessesCheckBox;

  private JComboBox myDebuggerTypeCombo;
  private JLabel myDebuggerLabel;

  private String myLastSelectedDevice;
  private String myLastSelectedProcess;

  private final MergingUpdateQueue myUpdatesQueue;
  private final AndroidDebugBridge.IClientChangeListener myClientChangeListener;
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener;

  private Client mySelectedClient;
  private AndroidDebugger myAndroidDebugger;

  public AndroidProcessChooserDialog(@NotNull Project project, boolean showDebuggerSelection) {
    super(project);
    setTitle("Choose Process");

    myShowDebuggerSelection = showDebuggerSelection;

    myProject = project;
    myUpdatesQueue =
      new MergingUpdateQueue("AndroidProcessChooserDialogUpdatingQueue", 500, true, MergingUpdateQueue.ANY_COMPONENT, getDisposable());

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    myLastSelectedProcess = properties.getValue(DEBUGGABLE_PROCESS_PROPERTY);
    myLastSelectedDevice = properties.getValue(DEBUGGABLE_DEVICE_PROPERTY);
    String lastSelectedDebuggerId = properties.getValue(DEBUGGER_ID_PROPERTY);

    final boolean showAllProcesses = Boolean.parseBoolean(properties.getValue(SHOW_ALL_PROCESSES_PROPERTY));

    myShowAllProcessesCheckBox.setSelected(showAllProcesses);

    myClientChangeListener = (client, changeMask) -> updateTree();
    AndroidDebugBridge.addClientChangeListener(myClientChangeListener);

    myDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NotNull IDevice device) {
        updateTree();
      }

      @Override
      public void deviceDisconnected(@NotNull IDevice device) {
        updateTree();
      }

      @Override
      public void deviceChanged(@NotNull IDevice device, int changeMask) {
        updateTree();
      }
    };
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);

    myShowAllProcessesCheckBox.addActionListener(e -> updateTree());

    setupDebuggerSelection(showDebuggerSelection, lastSelectedDebuggerId);

    myProcessTree.addTreeSelectionListener(e -> {
      IDevice selectedDevice = getSelectedDevice();
      Client selectedClient = getSelectedClient();

      myLastSelectedDevice = getPersistableName(selectedDevice);
      myLastSelectedProcess = getPersistableName(selectedClient);

      getOKAction().setEnabled(selectedDevice != null && selectedClient != null);
    });

    TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myProcessTree) {
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

    myCellRenderer = new MyProcessTreeCellRenderer(treeSpeedSearch, DeviceRenderer.shouldShowSerialNumbers(getDeviceList()));
    myProcessTree.setCellRenderer(myCellRenderer);

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

    doUpdateTree(showAllProcesses);
    init();
  }

  private void setupDebuggerSelection(boolean showDebuggerSelection, @Nullable String lastSelectedDebuggerId) {
    if (!showDebuggerSelection) {
      myDebuggerLabel.setVisible(false);
      myDebuggerTypeCombo.setVisible(false);
      return;
    }

    AndroidDebugger selectedDebugger = null;
    AndroidDebugger defaultDebugger = null;
    List<AndroidDebugger> androidDebuggers = Lists.newLinkedList();
    for (AndroidDebugger androidDebugger : AndroidDebugger.EP_NAME.getExtensions()) {
      if (androidDebugger.supportsProject(myProject)) {
        androidDebuggers.add(androidDebugger);
        if (selectedDebugger == null &&
            lastSelectedDebuggerId != null &&
            androidDebugger.getId().equals(lastSelectedDebuggerId)) {
          selectedDebugger = androidDebugger;
        }
        else if (androidDebugger.shouldBeDefault()) {
          defaultDebugger = androidDebugger;
        }
      }
    }

    if (selectedDebugger == null) {
      selectedDebugger = defaultDebugger;
    }

    androidDebuggers.sort((left, right) -> left.getId().compareTo(right.getId()));
    myDebuggerTypeCombo.setModel(new CollectionComboBoxModel(androidDebuggers));
    myDebuggerTypeCombo.setRenderer(new AndroidDebugger.Renderer());
    if (selectedDebugger != null) {
      myDebuggerTypeCombo.setSelectedItem(selectedDebugger);
    }
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
  protected void doHelpAction() {
    BrowserUtil.browse("https://developer.android.com/studio/debug/index.html");
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
          ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showErrorDialog(myContentPanel, AndroidBundle.message("ddms.corrupted.error"));
            AndroidProcessChooserDialog.this.close(1);
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

  private List<IDevice> getDeviceList() {
    final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myProject);
    if (debugBridge == null) {
      return Collections.EMPTY_LIST;
    }
    return Arrays.asList(debugBridge.getDevices());
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

      List<Client> clients = Lists.newArrayList(device.getClients());
      Collections.sort(clients, (c1, c2) -> {
        String n1 = StringUtil.notNullize(c1.getClientData().getClientDescription());
        String n2 = StringUtil.notNullize(c2.getClientData().getClientDescription());
        return n1.compareTo(n2);
      });

      for (Client client : clients) {
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

    myCellRenderer.setShowSerial(DeviceRenderer.shouldShowSerialNumbers(Arrays.asList(devices)));

    UIUtil.invokeLaterIfNeeded(() -> {
      myProcessTree.setModel(model);

      if (pathToSelect != null) {
        myProcessTree.getSelectionModel().setSelectionPath(new TreePath(pathToSelect));
      }
      else {
        getOKAction().setEnabled(false);
      }

      TreeUtil.expandAll(myProcessTree);
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

    mySelectedClient = getSelectedClient();
    if (mySelectedClient == null) {
      return;
    }

    myAndroidDebugger = (AndroidDebugger)myDebuggerTypeCombo.getSelectedItem();

    properties.setValue(DEBUGGABLE_DEVICE_PROPERTY, getPersistableName(selectedDevice));
    properties.setValue(DEBUGGABLE_PROCESS_PROPERTY, getPersistableName(mySelectedClient));
    properties.setValue(SHOW_ALL_PROCESSES_PROPERTY, Boolean.toString(myShowAllProcessesCheckBox.isSelected()));

    if (myAndroidDebugger != null) {
      properties.setValue(DEBUGGER_ID_PROPERTY, myAndroidDebugger.getId());
    }

    super.doOKAction();
  }

  /**
   * Returns the client that was selected if OK was pressed, null otherwise.
   */
  @Nullable
  public Client getClient() {
    return mySelectedClient;
  }

  @NotNull
  public AndroidDebugger getAndroidDebugger() {
    assert myShowDebuggerSelection : "Cannot obtain debugger after constructing dialog w/o debugger selection combo";
    return myAndroidDebugger;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidProcessChooserDialog";
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

  private static class MyProcessTreeCellRenderer extends ColoredTreeCellRenderer {
    private final TreeSpeedSearch mySpeedSearch;
    private boolean myShowSerial;

    public MyProcessTreeCellRenderer(@NotNull TreeSpeedSearch treeSpeedSearch, boolean showSerial) {
      mySpeedSearch = treeSpeedSearch;
      myShowSerial = showSerial;
    }

    public void setShowSerial(boolean showSerial) {
      myShowSerial = showSerial;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return;
      }

      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof IDevice) {
        DeviceRenderer.renderDeviceName((IDevice)userObject, this, myShowSerial);
      }
      else if (userObject instanceof Client) {
        final ClientData clientData = ((Client)userObject).getClientData();

        SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), clientData.getClientDescription(), attr.getStyle(), attr.getFgColor(),
                                   attr.getBgColor(), this);

        if (clientData.isValidUserId() && clientData.getUserId() != 0) {
          append(" (user " + Integer.toString(clientData.getUserId()) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }
  }
}
