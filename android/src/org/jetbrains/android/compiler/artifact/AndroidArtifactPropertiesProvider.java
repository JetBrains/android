package org.jetbrains.android.compiler.artifact;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactPropertiesProvider extends ArtifactPropertiesProvider {

  public static final String ANDROID_PROPERTIES_ID = "android-properties";

  protected AndroidArtifactPropertiesProvider() {
    super(ANDROID_PROPERTIES_ID);
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof AndroidApplicationArtifactType;
  }

  @NotNull
  @Override
  public ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new AndroidApplicationArtifactProperties();
  }

  public static AndroidArtifactPropertiesProvider getInstance() {
    return EP_NAME.findExtension(AndroidArtifactPropertiesProvider.class);
  }
}
