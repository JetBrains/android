package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.intellij.database.dataSource.AbstractDataSourceConfigurable;
import com.intellij.database.util.DbImplUtil;
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
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDataSourcePropertiesDialog extends AbstractDataSourceConfigurable<AndroidDbManager, AndroidDataSource> implements
                                                                                                                           Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDataSourcePropertiesDialog");
  private static final String[] DEFAULT_EXTERNAL_DB_PATTERNS = new String[]{"files/"};

  private DefaultComboBoxModel myDeviceComboBoxModel = new DefaultComboBoxModel();
  private String myMissingDeviceIds;

  private ComboBox myDeviceComboBox;
  private ComboBox myPackageNameComboBox;
  private ComboBox myDataBaseComboBox;
  private JPanel myPanel;
  private FieldPanel myNameField;
  private JPanel myConfigurationPanel;
  private JBRadioButton myExternalStorageRadioButton;
  private JBRadioButton myInternalStorageRadioButton;

  private IDevice mySelectedDevice = null;
  private final Map<String, List<String>> myDatabaseMap = ContainerUtil.newLinkedHashMap();
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceListener;

  private final AndroidDataSource myTempDataSource;

  protected AndroidDataSourcePropertiesDialog(@NotNull AndroidDbManager manager, @NotNull Project project, @NotNull AndroidDataSource dataSource) {
    super(manager, dataSource, project);

    myPanel.setBorder(DsUiDefaults.DEFAULT_PANEL_BORDER);
    myTempDataSource = dataSource.copy();

    myConfigurationPanel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myNameField.setLabelText("Name:");
    myNameField.createComponent();

    myDeviceComboBox.setRenderer(new DeviceRenderer.DeviceComboBoxRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof String) {
          append(AndroidDbUtil.getPresentableNameFromDeviceId((String)value));
        }
        else {
          super.customizeCellRenderer(list, value, index, selected, hasFocus);
        }
      }
    });
    myDeviceComboBox.setPreferredSize(new Dimension(JBUI.scale(300), myDeviceComboBox.getPreferredSize().height));

    myDeviceListener = new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(IDevice device) {
        addDeviceToComboBoxIfNeeded(device);
      }

      @Override
      public void deviceDisconnected(IDevice device) {
      }

      @Override
      public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) == changeMask) {
          addDeviceToComboBoxIfNeeded(device);
        }
      }
    };

    myDeviceComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDataBases();
      }
    });

    ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDbCombo();
      }
    };
    myPackageNameComboBox.addActionListener(l);
    myExternalStorageRadioButton.addActionListener(l);
    myInternalStorageRadioButton.addActionListener(l);

    new UiNotifyConnector.Once(myPanel, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        loadDevices();
        updateDataBases();
        updateDbCombo();
        registerDeviceListener();
      }
    });
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
          final Object element = myDeviceComboBoxModel.getElementAt(i);

          if (device.equals(element)) {
            return;
          }
        }
        myDeviceComboBoxModel.addElement(device);

        if (myMissingDeviceIds != null && myMissingDeviceIds.equals(deviceId)) {
          myDeviceComboBoxModel.removeElement(myMissingDeviceIds);
          myMissingDeviceIds = null;
        }
      }
    }, ModalityState.stateForComponent(myPanel));
  }

  private void loadDevices() {
    final AndroidDebugBridge bridge = AndroidSdkUtils.getDebugBridge(myProject);
    final IDevice[] devices = bridge != null ? getDevicesWithValidDeviceId(bridge) : new IDevice[0];
    final String deviceId = myDataSource.getState().deviceId;
    final DefaultComboBoxModel model = new DefaultComboBoxModel(devices);
    Object selectedItem = null;

    if (deviceId != null && deviceId.length() > 0) {
      for (IDevice device : devices) {
        if (deviceId.equals(AndroidDbUtil.getDeviceId(device))) {
          selectedItem = device;
          break;
        }
      }

      if (selectedItem == null) {
        model.addElement(deviceId);
        myMissingDeviceIds = deviceId;
        selectedItem = deviceId;
      }
    }
    myDeviceComboBoxModel = model;
    myDeviceComboBox.setModel(model);

    if (selectedItem != null) {
      myDeviceComboBox.setSelectedItem(selectedItem);
    }
  }

  @NotNull
  private static IDevice[] getDevicesWithValidDeviceId(@NotNull AndroidDebugBridge bridge) {
    final List<IDevice> result = new ArrayList<IDevice>();

    for (IDevice device : bridge.getDevices()) {
      if (device.isOnline()) {
        final String deviceId = AndroidDbUtil.getDeviceId(device);

        if (deviceId != null && deviceId.length() > 0) {
          result.add(device);
        }
      }
    }
    return result.toArray(new IDevice[result.size()]);
  }

  private void updateDataBases() {
    if (!myPanel.isShowing()) return;
    final Object selectedItem = myDeviceComboBox.getSelectedItem();
    IDevice selectedDevice = selectedItem instanceof IDevice ? (IDevice)selectedItem : null;

    if (selectedDevice == null) {
      myDatabaseMap.clear();
      myPackageNameComboBox.setModel(new DefaultComboBoxModel());
      myDataBaseComboBox.setModel(new DefaultComboBoxModel());
    }
    else if (!selectedDevice.equals(mySelectedDevice)) {
      loadDatabases(selectedDevice);
      myPackageNameComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(myDatabaseMap.keySet())));
      updateDbCombo();
    }
    mySelectedDevice = selectedDevice;
  }

  private void updateDbCombo() {
    if (!myPanel.isShowing()) return; // comboboxes do weird stuff when loosing focus
    String selectedPackage = getSelectedPackage();
    String selectedDatabase = getSelectedDatabase();
    boolean databaseIsCustom = StringUtil.isNotEmpty(selectedDatabase) && ((DefaultComboBoxModel)myDataBaseComboBox.getModel()).getIndexOf(selectedDatabase) < 0;

    if (myInternalStorageRadioButton.isSelected()) {
      List<String> dbList = myDatabaseMap.get(selectedPackage);
      myDataBaseComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(dbList)));
    }
    else {
      myDataBaseComboBox.setModel(new DefaultComboBoxModel(DEFAULT_EXTERNAL_DB_PATTERNS));
    }
    if (databaseIsCustom) {
      myDataBaseComboBox.getEditor().setItem(selectedDatabase);
    }
  }

  @NotNull
  private String getSelectedPackage() {
    return (String)myPackageNameComboBox.getEditor().getItem();
  }

  @NotNull
  private String getSelectedDatabase() {
    return (String)myDataBaseComboBox.getEditor().getItem();
  }

  private void loadDatabases(@NotNull IDevice device) {
    myDatabaseMap.clear();

    final FileListingService service = device.getFileListingService();
    if (service == null) return;

    final Set<String> packages = new HashSet<String>();

    for (AndroidFacet facet : ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID)) {
      final Manifest manifest = facet.getManifest();

      if (manifest != null) {
        final String aPackage = manifest.getPackage().getStringValue();

        if (aPackage != null && aPackage.length() > 0) {
          packages.add(aPackage);
        }
      }
    }
    if (packages.isEmpty()) return;

    final long startTime = System.currentTimeMillis();
    boolean tooLong = false;

    for (String aPackage : packages) {
      myDatabaseMap.put(aPackage, tooLong ? Collections.<String>emptyList(): loadDatabases(device, aPackage));

      if (System.currentTimeMillis() - startTime > 4000) {
        tooLong = true;
      }
    }
  }

  @NotNull
  private static List<String> loadDatabases(@NotNull IDevice device, @NotNull final String packageName) {
    final List<String> result = new ArrayList<String>();

    try {
      device.executeShellCommand("run-as " + packageName + " ls " + AndroidDbUtil.getInternalDatabasesRemoteDirPath(packageName), new MultiLineReceiver() {
        @Override
        public void processNewLines(String[] lines) {
          for (String line : lines) {
            if (line.length() > 0 && !line.contains(" ")) {
              result.add(line);
            }
          }
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      }, 2, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      LOG.debug(e);
    }
    return result;
  }

  private String getSelectedDeviceId() {
    Object item = myDeviceComboBox.getSelectedItem();
    if (item == null) return null; // "no devices" case should not throw AE

    if (item instanceof String) return (String)item;

    assert item instanceof IDevice;
    final String deviceId = AndroidDbUtil.getDeviceId((IDevice)item);
    return deviceId != null ? deviceId : "";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  public void saveData(@NotNull AndroidDataSource dataSource) {
    dataSource.setName(getNameValue());
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

    if (DbImplUtil.canConnectTo(myDataSource)) {
      AndroidSynchronizeHandler.doSynchronize(myProject, Collections.singletonList(myDataSource));
    }

    if (isNewDataSource()) {
      myManager.processAddOrRemove(myDataSource, true);
    }
  }

  @Override
  public void reset() {
    AndroidDataSource.State state = myDataSource.getState();
    myNameField.setText(StringUtil.notNullize(myDataSource.getName()));

    myInternalStorageRadioButton.setSelected(!state.external);
    myExternalStorageRadioButton.setSelected(state.external);

    myPackageNameComboBox.getEditor().setItem(StringUtil.notNullize(state.packageName));
    myDataBaseComboBox.getEditor().setItem(StringUtil.notNullize(state.databaseName));
  }

  private void registerDeviceListener() {
    AndroidDebugBridge.addDeviceChangeListener(myDeviceListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        AndroidDebugBridge.removeDeviceChangeListener(myDeviceListener);
      }
    });
  }

  @Override
  public void dispose() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return getNameValue();
  }

  private String getNameValue() {
    return myNameField.getText().trim();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null; // todo
  }

  public boolean isModified() {
    if (isNewDataSource()) return true;
    AndroidDataSource tempDataSource = getTempDataSource();

    if (!StringUtil.equals(tempDataSource.getName(), myDataSource.getName())) return true;
    return !tempDataSource.equalConfiguration(myDataSource);
  }
}
