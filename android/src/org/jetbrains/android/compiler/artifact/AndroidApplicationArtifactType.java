package org.jetbrains.android.compiler.artifact;

import com.intellij.facet.FacetModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.ModuleOutputSourceItem;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.PackagingSourceItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class AndroidApplicationArtifactType extends ArtifactType {
  public AndroidApplicationArtifactType() {
    super("apk", AndroidBundle.messagePointer("android.application.title"));
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public boolean isSuitableItem(@NotNull PackagingSourceItem item) {
    return !(item instanceof ModuleOutputSourceItem || item instanceof LibrarySourceItem);
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArchive(ArtifactUtil.suggestArtifactFileName(artifactName) + ".apk");
  }


  @NotNull
  @Override
  public List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    final List<AndroidFacet> facets = new ArrayList<>();

    for (Module module : context.getModulesProvider().getModules()) {
      final FacetModel facetModel = context.getModulesProvider().getFacetModel(module);
      final AndroidFacet facet = facetModel.getFacetByType(AndroidFacet.ID);

      if (facet != null && facet.getConfiguration().isAppProject()) {
        facets.add(facet);
      }
    }

    if (facets.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new MyTemplate(context.getProject(), facets));
  }

  public static ArtifactType getInstance() {
    return EP_NAME.findExtension(AndroidApplicationArtifactType.class);
  }

  private class MyTemplate extends ArtifactTemplate {
    private final Project myProject;
    private final List<AndroidFacet> myFacets;

    private MyTemplate(@NotNull Project project, @NotNull List<AndroidFacet> facets) {
      assert !facets.isEmpty();
      myProject = project;
      myFacets = facets;
    }

    @Override
    public String getPresentableName() {
      return myFacets.size() == 1
             ? "From module '" + myFacets.get(0).getModule().getName() + "'"
             : "From module...";
    }

    @Override
    public NewArtifactConfiguration createArtifact() {
      final AndroidFacet facet = myFacets.size() == 1
                                 ? myFacets.get(0)
                                 : AndroidArtifactUtil.chooseAndroidApplicationModule(myProject, myFacets);
      if (facet == null) {
        return null;
      }

      final CompositePackagingElement<?> rootElement =
        AndroidApplicationArtifactType.this.createRootElement(facet.getModule().getName());
      rootElement.addFirstChild(new AndroidFinalPackageElement(myProject, facet));
      return new NewArtifactConfiguration(rootElement, facet.getModule().getName(), AndroidApplicationArtifactType.this);
    }

    @Override
    public void setUpArtifact(@NotNull ModifiableArtifact artifact, @NotNull NewArtifactConfiguration configuration) {
      final AndroidFacet facet = AndroidArtifactUtil.getPackagedFacet(myProject, artifact);

      if (facet != null) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());

        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;
          p.setProGuardCfgFiles(facet.getProperties().myProGuardCfgFiles);
          // Properties should be set again in the new project model
          artifact.setProperties(AndroidArtifactPropertiesProvider.getInstance(), properties);
        }
      }
    }
  }
}
