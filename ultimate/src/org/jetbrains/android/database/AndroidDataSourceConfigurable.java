package org.jetbrains.android.database;

import com.android.builder.model.Variant;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.database.dataSource.AbstractDataSourceConfigurable;
import com.intellij.database.dataSource.DatabaseNameComponent;
import com.intellij.database.util.DbImplUtilCore;
import com.intellij.database.view.ui.DsUiDefaults;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class AndroidDataSourceConfigurable extends AbstractDataSourceConfigurable<AndroidDataSourceManager, AndroidDataSource> implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDataSourcePropertiesDialog");
  private static final String[] DEFAULT_EXTERNAL_DB_PATTERNS = new String[]{"files/"};

  private DefaultComboBoxModel<AndroidSourceDevice> myDeviceComboBoxModel;
  private DatabaseNameComponent myNameComponent;

  private ComboBox<AndroidSourceDevice> myDeviceComboBox;
  private ComboBox<String> myPackageNameComboBox;
  private ComboBox<String> myDataBaseComboBox;
  private JPanel myPanel;
  private JPanel myConfigurationPanel;
  private JBRadioButton myExternalStorageRadioButton;
  private JBRadioButton myInternalStorageRadioButton;

  private final AndroidDebugBridge.IDeviceChangeListener myDeviceListener;

  private final AndroidDataSource myTempDataSource;

  private volatile ListenableFuture<?> latestDbListRequest;

  protected AndroidDataSourceConfigurable(@NotNull AndroidDataSourceManager manager,
                                          @NotNull Project project,
                                          @NotNull AndroidDataSource dataSource) {
    super(manager, dataSource, project);
    myTempDataSource = dataSource.copy(true);
    myDeviceListener = new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(IDevice device) {
        addDeviceToComboBoxIfNeeded(device);
      }

      @Override
      public void deviceDisconnected(IDevice device) {
        myDeviceComboBox.repaint();
      }

      @Override
      public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) == changeMask) {
          addDeviceToComboBoxIfNeeded(device);
        }
      }
    };
  }

  private static class AndroidSourceDevice {
    @NotNull final String deviceId;
    @Nullable IDevice device;

    AndroidSourceDevice(@NotNull String deviceId) {
      this.deviceId = deviceId;
      device = null;
    }

    AndroidSourceDevice(@NotNull IDevice device, @NotNull String deviceId) {
      this.device = device;
      this.deviceId = deviceId;
    }

    public boolean deviceIdEquals(String otherDeviceId) {
      return deviceId.equals(otherDeviceId);
    }

    public void updateDevice(IDevice device) {
      this.device = device;
    }
  }

  private class DeviceCellRenderer extends ColoredListCellRenderer<AndroidSourceDevice> {
    private static final boolean SHOW_SERIAL = false;
    private static final String EMPTY_TEXT = "No Connected Devices";
    private final DeviceNamePropertiesProvider deviceNamePropertiesProvider;

    DeviceCellRenderer(Disposable parent) {
      deviceNamePropertiesProvider = new DeviceNamePropertiesFetcher(parent, new FutureCallback<DeviceNameProperties>() {
        @Override
        public void onSuccess(DeviceNameProperties result) {
          myDeviceComboBox.repaint();
        }

        @Override
        public void onFailure(@NotNull Throwable t) {

        }
      });
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, AndroidSourceDevice value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        append(EMPTY_TEXT, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value.device != null) {
        DeviceRenderer.renderDeviceName(value.device, deviceNamePropertiesProvider.get(value.device), this, SHOW_SERIAL);
      }
      else {
        append(value.deviceId, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myNameComponent = new DatabaseNameComponent(this, myController, true);
    myPanel.add(myNameComponent.getComponent(), BorderLayout.NORTH);
    myConfigurationPanel.setBorder(DsUiDefaults.DEFAULT_PANEL_BORDER);

    myDeviceComboBox.setRenderer(new DeviceCellRenderer(this));
    myDeviceComboBox.setPreferredSize(new Dimension(JBUIScale.scale(300), myDeviceComboBox.getPreferredSize().height));
    myDeviceComboBox.addActionListener(e -> updateDbCombo());

    myPackageNameComboBox.addActionListener(e -> updateDbCombo());
    myExternalStorageRadioButton.addActionListener(e -> updateDbCombo());
    myInternalStorageRadioButton.addActionListener(e -> updateDbCombo());

    new UiNotifyConnector.Once(myPanel, new Activatable() {
      @Override
      public void showNotify() {
        loadDevices();
        updatePackageCombo();
        registerDeviceListener();
      }
    });
    return myPanel;
  }

  @NotNull
  @Override
  public AndroidDataSource getTempDataSource() {
    saveData(myTempDataSource);
    return myTempDataSource;
  }

  private void addDeviceToComboBoxIfNeeded(@NotNull final IDevice device) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!device.isOnline()) {
          return;
        }
        final String deviceId = AndroidDbUtil.getDeviceId(device);

        if (deviceId == null || deviceId.length() == 0) {
          return;
        }
        for (int i = 0; i < myDeviceComboBoxModel.getSize(); i++) {
          final AndroidSourceDevice element = myDeviceComboBoxModel.getElementAt(i);

          if (element.deviceIdEquals(deviceId)) {
            element.updateDevice(device);
            myDeviceComboBox.repaint();
            return;
          }
        }
        myDeviceComboBoxModel.addElement(new AndroidSourceDevice(device, deviceId));
      }
    }, ModalityState.stateForComponent(myPanel));
  }

  private void loadDevices() {
    final AndroidDebugBridge bridge = AndroidSdkUtils.getDebugBridge(myProject);
    final AndroidSourceDevice[] devices = bridge != null ? getDevicesWithValidDeviceId(bridge) : new AndroidSourceDevice[0];
    final String deviceId = myDataSource.getState().deviceId;
    final DefaultComboBoxModel<AndroidSourceDevice> model = new DefaultComboBoxModel<>(devices);
    AndroidSourceDevice selectedItem = null;

    if (deviceId != null && deviceId.length() > 0) {
      for (AndroidSourceDevice device : devices) {
        if (device.deviceIdEquals(deviceId)) {
          selectedItem = device;
          break;
        }
      }

      if (selectedItem == null) {
        selectedItem = new AndroidSourceDevice(deviceId);
        model.addElement(selectedItem);
      }
    }
    myDeviceComboBoxModel = model;
    myDeviceComboBox.setModel(model);

    if (selectedItem != null) {
      myDeviceComboBox.setSelectedItem(selectedItem);
    }
  }

  @NotNull
  private static AndroidSourceDevice[] getDevicesWithValidDeviceId(@NotNull AndroidDebugBridge bridge) {
    final List<AndroidSourceDevice> result = new ArrayList<>();

    for (IDevice device : bridge.getDevices()) {
      if (device.isOnline()) {
        final String deviceId = AndroidDbUtil.getDeviceId(device);

        if (deviceId != null && deviceId.length() > 0) {
          result.add(new AndroidSourceDevice(device, deviceId));
        }
      }
    }
    return result.toArray(new AndroidSourceDevice[0]);
  }

  @RequiresEdt
  private void updateDbCombo() {
    if (!myPanel.isShowing()) return; // comboboxes do weird stuff when loosing focus
    IDevice selectedDevice = getSelectedDevice();
    String selectedPackage = getSelectedPackage();
    boolean wasSelectedFromList = isSelectedFromList(myDataBaseComboBox);

    if (latestDbListRequest != null && !latestDbListRequest.isDone()) {
      latestDbListRequest.cancel(false);
    }
    ListenableFuture<List<String>> futureDatabases = loadDatabases(selectedDevice, selectedPackage);
    latestDbListRequest = futureDatabases;

    Futures.addCallback(futureDatabases, new FutureCallback<List<String>>() {
      @Override
      public void onSuccess(List<String> resultList) {
        if (latestDbListRequest != futureDatabases) {
          return; // newer request is in progress
        }
        String newSelectedDatabase = getSelectedDatabase(); // user might have changed the value while the list was preparing in background
        String selectedItem = !wasSelectedFromList ? newSelectedDatabase : null;

        setComboItemsAndSelection(resultList, selectedItem, myDataBaseComboBox);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        String newSelectedDatabase = getSelectedDatabase(); // user might have changed the value while the list was preparing in background
        setComboItemsAndSelection(Collections.emptyList(), newSelectedDatabase, myDataBaseComboBox);
        LOG.debug(t);
      }
    }, EdtExecutorService.getInstance());
  }

  private void setComboItemsAndSelection(List<String> resultList, String selectedItem, ComboBox<String> comboBox) {
    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)comboBox.getModel();
    model.removeAllElements(); // this will also clear selected item
    if (selectedItem != null) {
      // set selection before adding elements to avoid redundant "selectionChanged" event
      model.setSelectedItem(selectedItem);
    }
    // this will select the first element, if selected item not set yet
    resultList.forEach(model::addElement); // Don't do "setModel" in order to prevent DropDownList closing (if it was opened)
  }

  @NotNull
  private ListenableFuture<List<String>> loadDatabases(IDevice selectedDevice, String selectedPackage) {
    ListenableFuture<List<String>> futureDatabases;
    if (myInternalStorageRadioButton.isSelected()) {
      if (selectedDevice == null) {
        futureDatabases = Futures.immediateFuture(Collections.emptyList());
      }
      else {
        futureDatabases = loadDatabasesFromInternalStorage(selectedDevice, selectedPackage);
      }
    }
    else {
      futureDatabases = Futures.immediateFuture(Arrays.asList(DEFAULT_EXTERNAL_DB_PATTERNS));
    }
    return futureDatabases;
  }

  private boolean isSelectedFromList(@NotNull ComboBox<String> comboBox) {
    String currentValue = (String)comboBox.getEditor().getItem();
    return StringUtil.isEmpty(currentValue) || ((DefaultComboBoxModel)comboBox.getModel()).getIndexOf(currentValue) >= 0;
  }

  @NotNull
  private String getSelectedPackage() {
    return (String)myPackageNameComboBox.getEditor().getItem();
  }

  @NotNull
  private String getSelectedDatabase() {
    return (String)myDataBaseComboBox.getEditor().getItem();
  }

  @RequiresEdt
  private void updatePackageCombo() {
    if (!myPanel.isShowing()) return; // comboboxes do weird stuff when loosing focus
    String selectedPackage = getSelectedPackage();
    boolean wasSelectedFromList = isSelectedFromList(myPackageNameComboBox);

    List<String> packages = loadPackageList();

    String selectedItem = !wasSelectedFromList ? selectedPackage : null;
    setComboItemsAndSelection(packages, selectedItem, myPackageNameComboBox);
  }

  private List<String> loadPackageList() {
    final Set<String> mainPackages = new HashSet<>();
    final Set<String> extraPackages = new HashSet<>();

    for (AndroidFacet facet : ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID)) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
      if (androidModuleModel != null) {
        IdeAndroidProject androidProject = androidModuleModel.getAndroidProject();

        for (Variant variant : androidProject.getVariants()) {
          mainPackages.add(variant.getMainArtifact().getApplicationId());
          if (variant.getExtraAndroidArtifacts() != null) {
            variant.getExtraAndroidArtifacts().forEach(artifact -> extraPackages.add(artifact.getApplicationId()));
          }
        }
      }
      else {
        // Non-Gradle Android modules do not have AndroidModuleModel. Use manifest directly.
        final Manifest manifest = Manifest.getMainManifest(facet);

        if (manifest != null) {
          final String aPackage = manifest.getPackage().getStringValue();

          if (aPackage != null && aPackage.length() > 0) {
            mainPackages.add(aPackage);
          }
        }
      }
    }
    if (mainPackages.isEmpty() && extraPackages.isEmpty()) return Collections.emptyList();

    extraPackages.removeAll(mainPackages);
    List<String> packages = new ArrayList<>(mainPackages.size() + extraPackages.size());
    packages.addAll(mainPackages);
    packages.addAll(extraPackages);
    return packages;
  }

  @NotNull
  private ListenableFuture<List<String>> loadDatabasesFromInternalStorage(@NotNull IDevice device, @NotNull final String packageName) {
    AdbDeviceFileSystemService fileSystemService = AdbDeviceFileSystemService.getInstance(getProject());
    AdbDeviceFileSystem remoteFileSystem = new AdbDeviceFileSystem(fileSystemService, device);

    String dbPath = AndroidDbUtil.getInternalDatabasesRemoteDirPath(packageName);
    ListenableFuture<DeviceFileEntry> remoteDatabasesPath = remoteFileSystem.getEntry(dbPath);

    ListenableFuture<List<DeviceFileEntry>> remoteFiles = Futures.transformAsync(remoteDatabasesPath,
                                                                                 p -> p.getEntries(),
                                                                                 PooledThreadExecutor.INSTANCE
    );

    return Futures.transform(remoteFiles,
                             l -> l.stream()
                               .map(DeviceFileEntry::getName)
                               .filter(name -> !name.endsWith("-journal"))
                               .collect(Collectors.toList()),
                             PooledThreadExecutor.INSTANCE
    );
  }

  @Nullable
  private IDevice getSelectedDevice() {
    AndroidSourceDevice item = (AndroidSourceDevice)myDeviceComboBox.getSelectedItem();
    if (item == null || item.device == null) {
      return null;
    }
    return item.device;
  }

  private String getSelectedDeviceId() {
    Object item = myDeviceComboBox.getSelectedItem();
    if (item == null) return null; // "no devices" case should not throw AE

    assert item instanceof AndroidSourceDevice;
    return ((AndroidSourceDevice)item).deviceId;
  }

  public void saveData(@NotNull AndroidDataSource dataSource) {
    myNameComponent.save(dataSource);
    AndroidDataSource.State state = dataSource.getState();
    state.deviceId = getSelectedDeviceId();
    state.packageName = getSelectedPackage();
    state.databaseName = getSelectedDatabase();
    state.external = myExternalStorageRadioButton.isSelected();
    dataSource.resetUrl();
  }

  @Override
  public void apply() {
    saveData(myDataSource);
    myNameComponent.apply(myDataSource);

    if (DbImplUtilCore.canConnectTo(myDataSource)) {
      AndroidSynchronizeHandler.doSynchronize(myProject, Collections.singletonList(myDataSource));
    }

    if (isNewDataSource()) {
      myManager.addDataSource(myDataSource);
    }
  }

  @Override
  protected void reset(@NotNull AndroidDataSource o) {
    AndroidDataSource.State state = o.getState();
    myNameComponent.reset(o, null);

    myInternalStorageRadioButton.setSelected(!state.external);
    myExternalStorageRadioButton.setSelected(state.external);

    myPackageNameComboBox.getEditor().setItem(StringUtil.notNullize(state.packageName));
    myDataBaseComboBox.getEditor().setItem(StringUtil.notNullize(state.databaseName));
  }

  @Override
  public JComponent getCommonBar() {
    return null;
  }

  private void registerDeviceListener() {
    AndroidDebugBridge.addDeviceChangeListener(myDeviceListener);
  }

  @Override
  public void dispose() {
    if (latestDbListRequest != null && !latestDbListRequest.isDone()) {
      latestDbListRequest.cancel(false);
    }

    AndroidDebugBridge.removeDeviceChangeListener(myDeviceListener);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameComponent.getPreferredFocusedComponent();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myNameComponent.getNameValue();
  }

  @Override
  public boolean isModified() {
    if (isNewDataSource()) return true;
    AndroidDataSource tempDataSource = getTempDataSource();

    if (!StringUtil.equals(tempDataSource.getName(), myDataSource.getName())) return true;
    return !tempDataSource.equalConfiguration(myDataSource) || myNameComponent.isModified();
  }
}
