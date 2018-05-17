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
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.editor.AndroidDebuggerInfoProvider;
import com.android.tools.idea.run.editor.RunConfigurationWithDebugger;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  // Dropdown to select Run Configuration.
  private JLabel myDebuggerRunConfigLabel;
  private JComboBox<RunConfigurationWithDebugger> myDebuggerRunConfigCombo;

  // Dropdown to select the debugger type.
  private JLabel myDebuggerLabel;
  private JComboBox<AndroidDebugger> myDebuggerTypeCombo;

  // Values cached in project properties about the most recent device/process selections of the user. These values are saved back to project
  // properties when the user clicks the OK button in the dialog.
  private String myLastSelectedDevice;
  private String myLastSelectedProcess;

  private final MergingUpdateQueue myUpdatesQueue;
  private final AndroidDebugBridge.IClientChangeListener myClientChangeListener;
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener;

  // Process, RunConfigurationWithDebugger, and DebuggerType selected by the user.
  private Client mySelectedClient;
  private RunConfigurationWithDebugger mySelectedRunConfiguration;
  private AndroidDebugger mySelectedAndroidDebugger;

  /**
   * @param project               the current project
   * @param showDebuggerSelection if false, the debugger-related dropdowns (e.g., run configuration, debugger type) are hidden.
   */
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

    FutureCallback<DeviceNameProperties> callback = new FutureCallback<DeviceNameProperties>() {
      @Override
      public void onSuccess(@Nullable DeviceNameProperties properties) {
        updateTree();
      }

      @Override
      public void onFailure(@NotNull Throwable throwable) {
        Logger.getInstance(AndroidProcessChooserDialog.class).warn("Error retrieving device name properties", throwable);
      }
    };

    DeviceNamePropertiesFetcher fetcher = new DeviceNamePropertiesFetcher(getDisposable(), callback);
    boolean showSerialNumbers = DeviceRenderer.shouldShowSerialNumbers(getDeviceList(), fetcher);

    myCellRenderer = new MyProcessTreeCellRenderer(treeSpeedSearch, showSerialNumbers, fetcher);
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
      myDebuggerRunConfigLabel.setVisible(false);
      myDebuggerRunConfigCombo.setVisible(false);
      return;
    }

    // If the user selects a different run configuration, we need to enable/disable and repopulate the DebuggerType combobox (e.g., change
    // the default selection).
    myDebuggerRunConfigCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDebuggerTypeCombo.setEnabled(myDebuggerRunConfigCombo.getSelectedItem() == null);
        myDebuggerLabel.setEnabled(myDebuggerRunConfigCombo.getSelectedItem() == null);

        final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
        String lastSelectedDebuggerId = properties.getValue(DEBUGGER_ID_PROPERTY);
        populateDebuggerTypeCombo((RunConfigurationWithDebugger)myDebuggerRunConfigCombo.getSelectedItem(), lastSelectedDebuggerId);
      }
    });

    // The attach dialog contains the project's run configurations.
    // We also add null to the front of the list to represent "[Create New]" run configuration.
    // Note we can't use ImmutableList here because ImmutableList doesn't allow null elements.
    List<RunConfigurationWithDebugger> existingValidRunConfigurations =
      ContainerUtil.filterIsInstance(
        RunManager.getInstance(myProject).getAllConfigurationsList(),
        RunConfigurationWithDebugger.class);
    ArrayList<RunConfigurationWithDebugger> runConfigurations = new ArrayList<>();
    runConfigurations.add(null);
    runConfigurations.addAll(existingValidRunConfigurations);
    myDebuggerRunConfigCombo.setModel(new CollectionComboBoxModel<>(runConfigurations));
    myDebuggerRunConfigCombo.setRenderer(SimpleListCellRenderer.create("[Use default settings]", RunConfigurationWithDebugger::getName));

    // The run configuration dropdown is initialized to the project's currently selected run configuration; if there is no run configuration,
    // then [Use default settings] remains as the default initial selection.
    RunConfiguration configuration = getCurrentRunConfiguration();
    if (!existingValidRunConfigurations.contains(configuration)) {
      configuration = null;
    }
    myDebuggerRunConfigCombo.setSelectedItem(configuration);

    // Initialize the DebuggerType dropdown contents and the selection.
    populateDebuggerTypeCombo((RunConfigurationWithDebugger)configuration, lastSelectedDebuggerId);
  }

  /**
   * Populates myDebuggerRunConfig combo box with a set of eligible debugger types (e.g., auto, native-only, etc), and selects one of them.
   *
   * @param configuration          if not null, debugger types and the selected debugger are taken from this run configuration
   * @param lastSelectedDebuggerId if configuration is null, this debugger Id is used as a hint to determine which debugger to select from
   *                               all possible debugger types supported by the current project
   */
  private void populateDebuggerTypeCombo(@Nullable RunConfigurationWithDebugger configuration, @Nullable String lastSelectedDebuggerId) {
    if (configuration != null) {
      myDebuggerRunConfigCombo.setSelectedItem(configuration);

      // If a run configuration is selected, the user is not allowed to change the debugger type from this dropdown.
      myDebuggerLabel.setEnabled(false);
      myDebuggerTypeCombo.setEnabled(false);
    }

    // Populate the debugger dropdown.
    List<AndroidDebugger> androidDebuggers = getAndroidDebuggers(configuration);
    androidDebuggers.sort((left, right) -> left.getId().compareTo(right.getId()));
    myDebuggerTypeCombo.setModel(new CollectionComboBoxModel(androidDebuggers));
    myDebuggerTypeCombo.setRenderer(SimpleListCellRenderer.create("", AndroidDebugger::getDisplayName));

    // Populate which entry is selected in the debugger dropdown (even if it's disabled).
    AndroidDebugger selectedDebugger = null;

    // if configuration is provided, get the currently selected debugger from the config.
    if (configuration != null) {
      for (AndroidDebuggerInfoProvider provider : AndroidDebuggerInfoProvider.EP_NAME.getExtensions()) {
        if (provider.supportsProject(myProject)) {
          selectedDebugger = provider.getSelectedAndroidDebugger(configuration);
          break;
        }
      }
    }

    // If we don't have a configuration, or we cannot extract debugger from the configuration, try to select the most recently
    // selected debugger.
    if (selectedDebugger == null) {
      AndroidDebugger defaultDebugger = null;
      for (AndroidDebugger androidDebugger : androidDebuggers) {
        if (selectedDebugger == null &&
            lastSelectedDebuggerId != null &&
            androidDebugger.getId().equals(lastSelectedDebuggerId)) {
          selectedDebugger = androidDebugger;
        }
        else if (androidDebugger.shouldBeDefault()) {
          defaultDebugger = androidDebugger;
        }
      }

      if (selectedDebugger == null) {
        // If there is no most recently selected debugger, then use the debugger that's marked as the default.
        // If there is no default, use the first debugger from the list.
        if (defaultDebugger != null) {
          selectedDebugger = defaultDebugger;
        }
        else if (!androidDebuggers.isEmpty()) {
          selectedDebugger = androidDebuggers.get(0);
        }
      }
    }

    if (selectedDebugger != null) {
      // The selected debugger may not be the same debugger object as one of the populated debuggers.
      // Here we choose the same "type" of debugger, not the same object.
      for (AndroidDebugger debugger : androidDebuggers) {
        if (debugger.getId().equals(selectedDebugger.getId())) {
          myDebuggerTypeCombo.setSelectedItem(debugger);
        }
      }
    }
  }

  /**
   * @param configuration if not null, then it is used as the source of debugger types; otherwise debuggers are obtained from
   *                      the extension points.
   * @return all android debuggers that should be populated in the debugger type dropdown.
   */
  private ArrayList<AndroidDebugger> getAndroidDebuggers(@Nullable RunConfigurationWithDebugger configuration) {
    if (configuration != null) {
      for (AndroidDebuggerInfoProvider provider : AndroidDebuggerInfoProvider.EP_NAME.getExtensions()) {
        if (!provider.supportsProject(myProject)) {
          continue;
        }
        return Lists.newArrayList(provider.getAndroidDebuggers(configuration));
      }
    }

    ArrayList<AndroidDebugger> androidDebuggers = new ArrayList<>();
    for (AndroidDebugger androidDebugger : AndroidDebugger.EP_NAME.getExtensions()) {
      if (!androidDebugger.supportsProject(myProject)) {
        continue;
      }
      androidDebuggers.add(androidDebugger);
    }

    return androidDebuggers;
  }

  /**
   * @return the RunConfigurationWithDebugger selected in the main UI for the current project, or null if there is no selection
   */
  @Nullable
  private RunConfiguration getCurrentRunConfiguration() {
    RunnerAndConfigurationSettings currentRunnerAndConfigurationSettings = RunManager.getInstance(myProject).getSelectedConfiguration();
    if (currentRunnerAndConfigurationSettings == null) {
      return null;
    }

    return currentRunnerAndConfigurationSettings.getConfiguration();
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

  @Nullable
  @Override
  protected String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/debug/index.html";
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
        if (debugBridge != null && isDdmsCorrupted(debugBridge)) {
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

    // doUpdateTree is called by updateTree which is called by the client and device listeners which are added to the Android debug bridge
    // before myCellRenderer is initialized. So myCellRenderer can be null here.
    if (myCellRenderer != null) {
      myCellRenderer.setShowSerial(Arrays.asList(devices));
    }

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
    final String lc = StringUtil.toLowerCase(clientDescription);

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
        result.add(StringUtil.toLowerCase(packageName));
      }
      final Manifest manifest = Manifest.getMainManifest(facet);

      if (manifest != null) {
        final XmlElement xmlElement = manifest.getXmlElement();

        if (xmlElement != null) {
          collectProcessNames(xmlElement, result);
        }
      }
      final AndroidModel androidModel = AndroidModel.get(facet);
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
            result.add(StringUtil.toLowerCase(value));
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

    mySelectedAndroidDebugger = (AndroidDebugger)myDebuggerTypeCombo.getSelectedItem();

    properties.setValue(DEBUGGABLE_DEVICE_PROPERTY, getPersistableName(selectedDevice));
    properties.setValue(DEBUGGABLE_PROCESS_PROPERTY, getPersistableName(mySelectedClient));
    properties.setValue(SHOW_ALL_PROCESSES_PROPERTY, Boolean.toString(myShowAllProcessesCheckBox.isSelected()));

    if (mySelectedAndroidDebugger != null) {
      properties.setValue(DEBUGGER_ID_PROPERTY, mySelectedAndroidDebugger.getId());
    }

    mySelectedRunConfiguration = (RunConfigurationWithDebugger)myDebuggerRunConfigCombo.getSelectedItem();

    super.doOKAction();
  }

  /**
   * Returns the client that was selected if OK was pressed, null otherwise.
   */
  @Nullable
  public Client getClient() {
    return mySelectedClient;
  }

  /**
   * Returns the run configuration that was selected if OK was pressed, null otherwise.
   */
  @Nullable
  public RunConfigurationWithDebugger getRunConfiguration() {
    return mySelectedRunConfiguration;
  }

  @NotNull
  public AndroidDebugger getSelectedAndroidDebugger() {
    assert myShowDebuggerSelection : "Cannot obtain debugger after constructing dialog w/o debugger selection combo";
    return mySelectedAndroidDebugger;
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

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // TODO: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  private static class MyProcessTreeCellRenderer extends ColoredTreeCellRenderer {
    private final TreeSpeedSearch mySpeedSearch;
    private boolean myShowSerial;
    private final DeviceNamePropertiesFetcher myDeviceNamePropertiesFetcher;

    private MyProcessTreeCellRenderer(@NotNull TreeSpeedSearch treeSpeedSearch,
                                      boolean showSerial,
                                      @NotNull DeviceNamePropertiesFetcher deviceNamePropertiesFetcher) {
      mySpeedSearch = treeSpeedSearch;
      myShowSerial = showSerial;
      myDeviceNamePropertiesFetcher = deviceNamePropertiesFetcher;
    }

    private void setShowSerial(@NotNull List<IDevice> devices) {
      myShowSerial = DeviceRenderer.shouldShowSerialNumbers(devices, myDeviceNamePropertiesFetcher);
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
      if (userObject instanceof IDevice && !Disposer.isDisposed(myDeviceNamePropertiesFetcher)) {
        IDevice device = (IDevice)userObject;
        DeviceRenderer.renderDeviceName(device, myDeviceNamePropertiesFetcher.get(device), this, myShowSerial);
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
