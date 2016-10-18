package org.jetbrains.jps.android.model;

import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface JpsAndroidApplicationArtifactProperties extends JpsElement {

  AndroidArtifactSigningMode getSigningMode();

  void setSigningMode(AndroidArtifactSigningMode mode);

  String getKeyStoreUrl();

  void setKeyStoreUrl(String url);

  String getKeyStorePassword();

  void setKeyStorePassword(String password);

  String getKeyAlias();

  void setKeyAlias(String alias);

  String getKeyPassword();

  void setKeyPassword(String password);

  boolean isRunProGuard();

  void setRunProGuard(boolean value);

  List<String> getProGuardCfgFiles();

  List<String> getProGuardCfgFiles(@NotNull JpsModule module);

  void setProGuardCfgFiles(List<String> url);
}
