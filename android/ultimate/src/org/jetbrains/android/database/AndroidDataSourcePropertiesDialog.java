package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tools.idea.ddms.DeviceComboBoxRenderer;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
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
public class AndroidDataSourcePropertiesDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDataSourcePropertiesDialog");

  private final Project myProject;
  private final AndroidDataSource myDataSource;
  private final boolean myCreate;

  private DefaultComboBoxModel myDeviceComboBoxModel = new DefaultComboBoxModel();
  private String myMissingDeviceIds;

  private ComboBox myDeviceComboBox;
  private ComboBox myPackageNameComboBox;
  private ComboBox myDataBaseComboBox;
  private JPanel myPanel;
  private JBTextField myNameTextField;

  private IDevice mySelectedDevice = null;
  private Map<String, List<String>> myDatabaseMap;

  protected AndroidDataSourcePropertiesDialog(@NotNull Project project, @NotNull AndroidDataSource dataSource, boolean create) {
    super(project);
    myProject = project;
    myDataSource = dataSource;
    myCreate = create;

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

    final AndroidDebugBridge.IDeviceChangeListener listener = new AndroidDebugBridge.IDeviceChangeListener() {
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
    AndroidDebugBridge.addDeviceChangeListener(listener);

    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        AndroidDebugBridge.removeDeviceChangeListener(listener);
      }
    });

    myDeviceComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDataBases();
      }
    });
    updateDataBases();

    myPackageNameComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDbCombo();
      }
    });
    updateDbCombo();

    final String name = dataSource.getName();
    myNameTextField.setText(name != null ? name : "");

    if (!create) {
      final AndroidDataSource.State state = dataSource.getState();
      final String packageName = state.getPackageName();
      myPackageNameComboBox.getEditor().setItem(packageName != null ? packageName : "");
      final String dbName = state.getDatabaseName();
      myDataBaseComboBox.getEditor().setItem(dbName != null ? dbName : "");
    }
    setTitle(myCreate ? "Create Android SQLite Data Source" : "Android SQLite Data Source Properties");

    myDeviceComboBox.setPreferredSize(new Dimension(300 , myDeviceComboBox.getPreferredSize().height));
    init();
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
        pack();
      }
    }, ModalityState.stateForComponent(myPanel));
  }

  private void loadDevices() {
    final AndroidDebugBridge bridge = AndroidSdkUtils.getDebugBridge(myProject);
    final IDevice[] devices = bridge != null ? getDevicesWithValidDeviceId(bridge) : new IDevice[0];
    final String deviceId = myDataSource.getState().getDeviceId();
    final DefaultComboBoxModel model = new DefaultComboBoxModel(devices);

    if (deviceId != null) {
      Object selectedItem = null;

      for (IDevice device : devices) {
        if (deviceId.equals(AndroidDbUtil.getDeviceId(device))) {
          selectedItem = device;
          break;
        }
      }

      if (!myCreate && selectedItem == null) {
        model.addElement(deviceId);
        myMissingDeviceIds = deviceId;
        selectedItem = deviceId;
      }
      myDeviceComboBoxModel = model;
      myDeviceComboBox.setModel(model);

      if (selectedItem != null) {
        myDeviceComboBox.setSelectedItem(selectedItem);
      }

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
    final List<String> dbList = myDatabaseMap.get(selectedPackage);
    myDataBaseComboBox.setModel(new DefaultComboBoxModel(
      dbList != null ? ArrayUtil.toStringArray(dbList) : ArrayUtil.EMPTY_STRING_ARRAY));
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
      device.executeShellCommand("run-as " + packageName + " ls /data/data/" + packageName + "/databases", new MultiLineReceiver() {
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

  public static boolean showPropertiesDialog(@NotNull AndroidDataSource dataSource, @NotNull Project project, boolean create) {
    return new AndroidDataSourcePropertiesDialog(project, dataSource, create).showAndGet();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
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

  @Override
  protected void doOKAction() {
    super.doOKAction();
    myDataSource.setName(myNameTextField.getText());
    final AndroidDataSource.State state = myDataSource.getState();
    state.setDeviceId(getSelectedDeviceId());
    state.setPackageName(getSelectedPackage());
    state.setDatabaseName(getSelectedDatabase());
    myDataSource.resetUrl();

    AndroidSynchronizeHandler.doSynchronize(myProject, Collections.singletonList(myDataSource));
    AndroidDbUtil.detectDriverAndRefresh(myProject, myDataSource, myCreate);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }
}
