package org.jetbrains.android.inspections;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidComponentEntryPoint extends EntryPoint {
  public boolean ADD_ANDROID_COMPONENTS_TO_ENTRIES = true;

  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.component.entry.point");
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    return ADD_ANDROID_COMPONENTS_TO_ENTRIES &&
           psiElement instanceof PsiClass &&
           AndroidUtils.isAndroidComponent((PsiClass)psiElement);
  }

  @Override
  public boolean isSelected() {
    return ADD_ANDROID_COMPONENTS_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_ANDROID_COMPONENTS_TO_ENTRIES = selected;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    XmlSerializer.deserializeInto(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
  }
}
