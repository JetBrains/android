package org.jetbrains.jps.android.model;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApplicationArtifactType extends JpsElementTypeBase<JpsAndroidApplicationArtifactProperties> implements JpsArtifactType<JpsAndroidApplicationArtifactProperties> {
  public static final AndroidApplicationArtifactType INSTANCE = new AndroidApplicationArtifactType();
}
