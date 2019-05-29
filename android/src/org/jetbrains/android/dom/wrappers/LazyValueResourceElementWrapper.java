package org.jetbrains.android.dom.wrappers;

import com.android.tools.idea.res.psi.ResourceNavigationItem;
import com.google.common.base.Stopwatch;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import com.intellij.psi.impl.RenameableFakePsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import javax.swing.Icon;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
public class LazyValueResourceElementWrapper extends RenameableFakePsiElement
    implements PsiTarget, Comparable<LazyValueResourceElementWrapper> {
  private static final Logger LOG = Logger.getInstance(LazyValueResourceElementWrapper.class);
  private final ValueResourceInfo myResourceInfo;
  private final PsiElement myParent;

  public LazyValueResourceElementWrapper(@NotNull ValueResourceInfo resourceInfo, @NotNull PsiElement parent) {
    super(parent);
    myParent = parent;
    myResourceInfo = resourceInfo;
  }

  @Override
  @NotNull
  public String getName() {
    return myResourceInfo.getName();
  }

  @Override
  @Nullable
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    XmlAttributeValue element = computeElement();
    if (element == null) {
      throw new IncorrectOperationException(
          "Cannot find resource '" + myResourceInfo.getName() + "' in file " + myResourceInfo.getContainingFile().getPath());
    }
    return new ValueResourceElementWrapper(element).setName(name);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ResourceNavigationItem.ResourceItemPresentation(myResourceInfo.getResource(), myResourceInfo.getContainingFile());
  }

  @Nullable
  public XmlAttributeValue computeElement() {
    if (LOG.isDebugEnabled()) {
      Stopwatch stopwatch = Stopwatch.createStarted();
      XmlAttributeValue value = myResourceInfo.computeXmlElement();
      LOG.debug("Computing XML element for lazy resource: " + this.myResourceInfo + ", time: " + stopwatch);
      return value;
    }
    else {
      return myResourceInfo.computeXmlElement();
    }
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    XmlAttributeValue element = myResourceInfo.computeXmlElement();
    return element != null ? element : myParent;
  }

  @NotNull
  public ValueResourceInfo getResourceInfo() {
    return myResourceInfo;
  }

  @Override
  @NotNull
  public String getTypeName() {
    return "Android Value Resource";
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  public static PsiElement computeLazyElement(PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      element = ((LazyValueResourceElementWrapper)element).computeElement();
    }
    return element;
  }

  // Comparator useful for comparing one wrapper for priority sorting without having to actually compute the XML elements
  @Override
  public int compareTo(@NotNull LazyValueResourceElementWrapper other) {
    return myResourceInfo.compareTo(other.myResourceInfo);
  }
}
