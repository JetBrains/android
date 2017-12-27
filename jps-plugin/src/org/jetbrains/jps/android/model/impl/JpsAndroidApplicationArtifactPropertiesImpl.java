package org.jetbrains.jps.android.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidApplicationArtifactProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidApplicationArtifactPropertiesImpl extends JpsElementBase<JpsAndroidApplicationArtifactPropertiesImpl>
  implements JpsAndroidApplicationArtifactProperties {

  public static final JpsElementChildRole<JpsAndroidApplicationArtifactProperties> ROLE =
    JpsElementChildRoleBase.create("android application artifact properties");

  private final MyState myState = new MyState();

  public JpsAndroidApplicationArtifactPropertiesImpl() {
  }

  public JpsAndroidApplicationArtifactPropertiesImpl(@NotNull MyState state) {
    myState.SIGNING_MODE = state.SIGNING_MODE;
    myState.KEY_STORE_URL = state.KEY_STORE_URL;
    myState.KEY_STORE_PASSWORD = state.KEY_STORE_PASSWORD;
    myState.KEY_ALIAS = state.KEY_ALIAS;
    myState.KEY_PASSWORD = state.KEY_PASSWORD;
    myState.PROGUARD_CFG_FILES = state.PROGUARD_CFG_FILES;
    myState.RUN_PROGUARD = state.RUN_PROGUARD;
  }

  @NotNull
  @Override
  public JpsAndroidApplicationArtifactPropertiesImpl createCopy() {
    return new JpsAndroidApplicationArtifactPropertiesImpl(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidApplicationArtifactPropertiesImpl modified) {
    setSigningMode(modified.getSigningMode());
    setKeyStoreUrl(modified.getKeyStoreUrl());
    setKeyStorePassword(modified.getKeyStorePassword());
    setKeyAlias(modified.getKeyAlias());
    setKeyPassword(modified.getKeyPassword());
    setRunProGuard(modified.isRunProGuard());
    setProGuardCfgFiles(modified.getProGuardCfgFiles());
  }

  @NotNull
  public MyState getState() {
    return myState;
  }

  @Override
  public AndroidArtifactSigningMode getSigningMode() {
    return myState.SIGNING_MODE;
  }

  @Override
  public void setSigningMode(AndroidArtifactSigningMode mode) {
    if (!myState.SIGNING_MODE.equals(mode)) {
      myState.SIGNING_MODE = mode;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyStoreUrl() {
    return myState.KEY_STORE_URL;
  }

  @Override
  public void setKeyStoreUrl(String url) {
    if (!myState.KEY_STORE_URL.equals(url)) {
      myState.KEY_STORE_URL = url;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyStorePassword() {
    return myState.KEY_STORE_PASSWORD;
  }

  @Override
  public void setKeyStorePassword(String password) {
    if (!myState.KEY_STORE_PASSWORD.equals(password)) {
      myState.KEY_STORE_PASSWORD = password;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyAlias() {
    return myState.KEY_ALIAS;
  }

  @Override
  public void setKeyAlias(String alias) {
    if (!myState.KEY_ALIAS.equals(alias)) {
      myState.KEY_ALIAS = alias;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyPassword() {
    return myState.KEY_PASSWORD;
  }

  @Override
  public void setKeyPassword(String password) {
    if (!myState.KEY_PASSWORD.equals(password)) {
      myState.KEY_PASSWORD = password;
      fireElementChanged();
    }
  }

  @Override
  public boolean isRunProGuard() {
    return myState.RUN_PROGUARD;
  }

  @Override
  public void setRunProGuard(boolean value) {
    if (myState.RUN_PROGUARD != value) {
      myState.RUN_PROGUARD = value;
      fireElementChanged();
    }
  }

  @Override
  public List<String> getProGuardCfgFiles() {
    return myState.PROGUARD_CFG_FILES;
  }

  @Override
  public List<String> getProGuardCfgFiles(@NotNull JpsModule module) {
    final List<String> urls = getProGuardCfgFiles();

    if (urls == null || urls.isEmpty()) {
      return urls;
    }
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
    final String sdkHomePath = sdk != null ? FileUtil.toSystemIndependentName(sdk.getHomePath()) : null;

    if (sdkHomePath == null || sdkHomePath.isEmpty()) {
      return urls;
    }
    final List<String> result = new ArrayList<String>(urls.size());

    for (String url : urls) {
      result.add(StringUtil.replace(url, AndroidCommonUtils.SDK_HOME_MACRO, sdkHomePath));
    }
    return result;
  }

  @Override
  public void setProGuardCfgFiles(List<String> urls) {
    if (!myState.PROGUARD_CFG_FILES.equals(urls)) {
      myState.PROGUARD_CFG_FILES = urls;
      fireElementChanged();
    }
  }

  public static class MyState {
    public AndroidArtifactSigningMode SIGNING_MODE = AndroidArtifactSigningMode.RELEASE_UNSIGNED;
    public String KEY_STORE_URL = "";
    public String KEY_STORE_PASSWORD = "";
    public String KEY_ALIAS = "";
    public String KEY_PASSWORD = "";
    public boolean RUN_PROGUARD;
    public List<String> PROGUARD_CFG_FILES = new ArrayList<String>();
  }
}