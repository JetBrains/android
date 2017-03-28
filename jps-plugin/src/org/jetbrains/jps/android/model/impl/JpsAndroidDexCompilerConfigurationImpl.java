package org.jetbrains.jps.android.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidDexCompilerConfigurationImpl extends JpsElementBase<JpsAndroidDexCompilerConfigurationImpl>
  implements JpsAndroidDexCompilerConfiguration {

  public static final JpsElementChildRole<JpsAndroidDexCompilerConfiguration> ROLE =
    JpsElementChildRoleBase.create("android dex compiler configuration");

  private final MyState myState = new MyState();

  public JpsAndroidDexCompilerConfigurationImpl() {
  }

  public JpsAndroidDexCompilerConfigurationImpl(@NotNull MyState state) {
    myState.MAX_HEAP_SIZE = state.MAX_HEAP_SIZE;
    myState.OPTIMIZE = state.OPTIMIZE;
    myState.VM_OPTIONS = state.VM_OPTIONS;
    myState.FORCE_JUMBO = state.FORCE_JUMBO;
    myState.CORE_LIBRARY = state.CORE_LIBRARY;
    myState.PROGUARD_VM_OPTIONS = state.PROGUARD_VM_OPTIONS;
  }

  @Override
  public String getVmOptions() {
    return myState.VM_OPTIONS;
  }

  @Override
  public void setVmOptions(String value) {
    if (!myState.VM_OPTIONS.equals(value)) {
      myState.VM_OPTIONS = value;
      fireElementChanged();
    }
  }

  @Override
  public int getMaxHeapSize() {
    return myState.MAX_HEAP_SIZE;
  }

  @Override
  public void setMaxHeapSize(int value) {
    if (myState.MAX_HEAP_SIZE != value) {
      myState.MAX_HEAP_SIZE = value;
      fireElementChanged();
    }
  }

  @Override
  public boolean isOptimize() {
    return myState.OPTIMIZE;
  }

  @Override
  public void setOptimize(boolean value) {
    if (myState.OPTIMIZE != value) {
      myState.OPTIMIZE = value;
      fireElementChanged();
    }
  }

  @Override
  public boolean isForceJumbo() {
    return myState.FORCE_JUMBO;
  }

  @Override
  public void setForceJumbo(boolean value) {
    if (myState.FORCE_JUMBO != value) {
      myState.FORCE_JUMBO = value;
      fireElementChanged();
    }
  }

  @Override
  public boolean isCoreLibrary() {
    return myState.CORE_LIBRARY;
  }

  @Override
  public void setCoreLibrary(boolean value) {
    if (myState.CORE_LIBRARY != value) {
      myState.CORE_LIBRARY = value;
      fireElementChanged();
    }
  }

  @Override
  public String getProguardVmOptions() {
    return myState.PROGUARD_VM_OPTIONS;
  }

  @Override
  public void setProguardVmOptions(String value) {
    if (!myState.PROGUARD_VM_OPTIONS.equals(value)) {
      myState.PROGUARD_VM_OPTIONS = value;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsAndroidDexCompilerConfigurationImpl createCopy() {
    return new JpsAndroidDexCompilerConfigurationImpl(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidDexCompilerConfigurationImpl modified) {
    setVmOptions(modified.getVmOptions());
    setMaxHeapSize(modified.getMaxHeapSize());
    setOptimize(modified.isOptimize());
    setForceJumbo(modified.isForceJumbo());
    setCoreLibrary(modified.isCoreLibrary());
    setProguardVmOptions(modified.getProguardVmOptions());
  }

  @NotNull
  public MyState getState() {
    return myState;
  }

  public static class MyState {
    public String VM_OPTIONS = "";
    public String PROGUARD_VM_OPTIONS = "";
    public int MAX_HEAP_SIZE = 1024;
    public boolean OPTIMIZE = true;
    public boolean FORCE_JUMBO = false;
    public boolean CORE_LIBRARY = false;
  }
}
