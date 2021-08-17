package org.jetbrains.android.compiler.artifact;

import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.FacetBasedPackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class AndroidFinalPackageElement extends PackagingElement<AndroidFinalPackageElement.AndroidFinalPackageElementState>
  implements FacetBasedPackagingElement, ModuleOutputPackagingElement {

  @NonNls static final String FACET_ATTRIBUTE = "facet";

  private FacetPointer<AndroidFacet> myFacetPointer;
  private final Project myProject;

  public AndroidFinalPackageElement(@NotNull Project project, @Nullable AndroidFacet facet) {
    super(AndroidFinalPackageElementType.getInstance());
    myProject = project;
    myFacetPointer = facet != null ? FacetPointersManager.getInstance(myProject).create(facet) : null;
  }

  @NotNull
  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new AndroidFinalPackagePresentation(myFacetPointer));
  }

  @Nullable
  public AndroidFacet getFacet() {
    return myFacetPointer != null ? myFacetPointer.getFacet() : null;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    if (!(element instanceof AndroidFinalPackageElement)) {
      return false;
    }
    final AndroidFinalPackageElement packageElement = (AndroidFinalPackageElement)element;

    return myFacetPointer == null
           ? packageElement.myFacetPointer == null
           : myFacetPointer.equals(packageElement.myFacetPointer);
  }

  @Override
  public AndroidFinalPackageElementState getState() {
    final AndroidFinalPackageElementState state = new AndroidFinalPackageElementState();
    state.myFacetPointer = myFacetPointer != null ? myFacetPointer.getId() : null;
    return state;
  }

  @Override
  public AndroidFacet findFacet(@NotNull PackagingElementResolvingContext context) {
    return myFacetPointer != null ? myFacetPointer.findFacet(context.getModulesProvider(), context.getFacetsProvider()) : null;
  }

  @Override
  public void loadState(@NotNull AndroidFinalPackageElementState state) {
    myFacetPointer = state.myFacetPointer != null
                     ? FacetPointersManager.getInstance(myProject).<AndroidFacet>create(state.myFacetPointer)
                     : null;
  }

  @Override
  public String getModuleName() {
    return myFacetPointer != null ? myFacetPointer.getModuleName() : null;
  }

  @Override
  public Module findModule(PackagingElementResolvingContext context) {
    final AndroidFacet facet = findFacet(context);
    return facet != null ? facet.getModule() : null;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getSourceRoots(PackagingElementResolvingContext context) {
    return Collections.emptyList();
  }

  public static class AndroidFinalPackageElementState {

    @Attribute(FACET_ATTRIBUTE)
    public String myFacetPointer;
  }
}
