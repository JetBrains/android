package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tools.idea.ddms.DeviceComboBoxRenderer;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.javaee.dataSource.AbstractDataSourceConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
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
public class AndroidDataSourcePropertiesDialog extends AbstractDataSourceConfigurable<AndroidDbManager, AndroidDataSource> {
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
  private Map<String, List<String>> myDatabaseMap;
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceListener;

  protected AndroidDataSourcePropertiesDialog(@NotNull AndroidDbManager manager, @NotNull Project project, @NotNull AndroidDataSource dataSource) {
    super(manager, dataSource, project);

    myConfigurationPanel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myNameField.setLabelText("Data Source Name");
    myNameField.createComponent();

    final AndroidDataSource.State state = dataSource.getState();
    myInternalStorageRadioButton.setSelected(!state.isExternal());
    myExternalStorageRadioButton.setSelected(state.isExternal());

    myDeviceComboBox.setRenderer(new DeviceComboBoxRenderer() {
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
    loadDevices();

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
    AndroidDebugBridge.addDeviceChangeListener(myDeviceListener);

    myDeviceComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDataBases();
      }
    });
    updateDataBases();

    final ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDbCombo();
      }
    };
    myPackageNameComboBox.addActionListener(l);
    updateDbCombo();

    final String name = dataSource.getName();
    myNameField.setText(name != null ? name : "");

    final String packageName = state.getPackageName();
    myPackageNameComboBox.getEditor().setItem(packageName != null ? packageName : "");
    final String dbName = state.getDatabaseName();
    myDataBaseComboBox.getEditor().setItem(dbName != null ? dbName : "");
    myDeviceComboBox.setPreferredSize(new Dimension(300, myDeviceComboBox.getPreferredSize().height));

    myExternalStorageRadioButton.addActionListener(l);
    myInternalStorageRadioButton.addActionListener(l);

    setChangeListener(myNameField);
    setChangeListener(myPackageNameComboBox);
    setChangeListener(myDataBaseComboBox);
    setChangeListener(myDeviceComboBox);
    setChangeListener(myExternalStorageRadioButton);
    setChangeListener(myInternalStorageRadioButton);
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
    final String deviceId = myDataSource.getState().getDeviceId();
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
    final Object selectedItem = myDeviceComboBox.getSelectedItem();
    IDevice selectedDevice = selectedItem instanceof IDevice ? (IDevice)selectedItem : null;

    if (selectedDevice == null) {
      myDatabaseMap = Collections.emptyMap();
      myPackageNameComboBox.setModel(new DefaultComboBoxModel());
      myDataBaseComboBox.setModel(new DefaultComboBoxModel());
    }
    else if (!selectedDevice.equals(mySelectedDevice)) {
      myDatabaseMap = loadDatabases(selectedDevice);
      myPackageNameComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(myDatabaseMap.keySet())));
      updateDbCombo();
    }
    mySelectedDevice = selectedDevice;
  }

  private void updateDbCombo() {
    final String selectedPackage = getSelectedPackage();

    if (myInternalStorageRadioButton.isSelected()) {
      final List<String> dbList = myDatabaseMap.get(selectedPackage);
      myDataBaseComboBox.setModel(new DefaultComboBoxModel(
        dbList != null ? ArrayUtil.toStringArray(dbList) : ArrayUtil.EMPTY_STRING_ARRAY));
    }
    else {
      myDataBaseComboBox.setModel(new DefaultComboBoxModel(DEFAULT_EXTERNAL_DB_PATTERNS));
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

  @NotNull
  private Map<String, List<String>> loadDatabases(@NotNull IDevice device) {
    final FileListingService service = device.getFileListingService();

    if (service == null) {
      return Collections.emptyMap();
    }
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
    if (packages.isEmpty()) {
      return Collections.emptyMap();
    }
    final Map<String, List<String>> result = new HashMap<String, List<String>>();
    final long startTime = System.currentTimeMillis();
    boolean tooLong = false;

    for (String aPackage : packages) {
      result.put(aPackage, tooLong ? Collections.<String>emptyList(): loadDatabases(device, aPackage));

      if (System.currentTimeMillis() - startTime > 4000) {
        tooLong = true;
      }
    }
    return result;
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

  @NotNull
  private String getSelectedDeviceId() {
    final Object item = myDeviceComboBox.getSelectedItem();

    if (item instanceof String) {
      return (String)item;
    }
    assert item instanceof IDevice;
    final String deviceId = AndroidDbUtil.getDeviceId((IDevice)item);
    return deviceId != null ? deviceId : "";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void apply() {
    myDataSource.setName(myNameField.getText());
    final AndroidDataSource.State state = myDataSource.getState();
    state.setDeviceId(getSelectedDeviceId());
    state.setPackageName(getSelectedPackage());
    state.setDatabaseName(getSelectedDatabase());
    state.setExternal(myExternalStorageRadioButton.isSelected());
    myDataSource.resetUrl();

    AndroidSynchronizeHandler.doSynchronize(myProject, Collections.singletonList(myDataSource));

    setModified(false);

    if (!isDataSourcePersisted()) {
      myManager.processAddOrRemove(myDataSource, true);
    }
  }

  @Override
  public void reset() {
    setModified(false);
  }

  @Override
  public void disposeUIResources() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceListener);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myNameField.getText();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }
}
