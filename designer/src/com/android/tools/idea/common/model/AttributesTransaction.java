/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.common.model;

import android.view.View;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.model.LayoutParamsManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;

/**
 * Class containing an {@link NlComponent} attributes transaction. All the modifications in this transaction are not committed until the
 * {@link #commit()} method is called. If {@link #rollback()} is called, all the changes are discarded.
 * A transaction can be used safely from multiple threads.
 *
 * TODO: remove layout-specific logic from this class
 */
public class AttributesTransaction implements NlAttributesHolder {
  private final NlComponent myComponent;

  /**
   * Lock that guards all the operations on the attributes below
   */
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final HashMap<String, PendingAttribute> myPendingAttributes = new HashMap<>();
  private final HashMap<String, String> myOriginalValues;
  private final NlModel myModel;
  private boolean isValid = true;
  /**
   * After calling commit (this will indicate if the transaction was successful
   */
  private boolean isSuccessful = false;
  @NotNull private WeakReference<View> myCachedView = new WeakReference<>(null);
  private boolean hasPendingRelayout;

  public AttributesTransaction(@NotNull NlComponent thisComponent) {
    myComponent = thisComponent;
    myModel = myComponent.getModel();

    List<AttributeSnapshot> attributes = myComponent.getAttributes();
    myOriginalValues = Maps.newHashMapWithExpectedSize(attributes.size());
    attributes.stream().forEach((attribute) -> myOriginalValues.put(attributeKey(attribute.namespace, attribute.name), attribute.value));
  }

  @NotNull
  private static String attributeKey(@Nullable String namespace, @NotNull String attribute) {
    return String.format("%s:%s", namespace, attribute);
  }

  /**
   * Apply the given {@link PendingAttribute} to the passed {@link ViewInfo}
   */
  private void applyAttributeToView(@NotNull PendingAttribute attribute, @NotNull ViewInfo viewInfo, NlModel model) {
    if (attribute.name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
      String value = attribute.value;
      Object layoutParams = viewInfo.getLayoutParamsObject();
      Object viewObject = viewInfo.getViewObject();
      if (viewObject == null || layoutParams == null) {
        return;
      }

      boolean changed = LayoutParamsManager
        .setAttribute(layoutParams, StringUtil.trimStart(attribute.name, ATTR_LAYOUT_RESOURCE_PREFIX), value, model);
      hasPendingRelayout |= changed;
    }
  }

  private static void triggerViewRelayout(@NotNull View view) {
    view.setLayoutParams(view.getLayoutParams());
    view.forceLayout();
  }

  /**
   * Applies all the existing attributes to the given ViewInfo info
   *
   * @param viewInfo
   */
  private void applyAllPendingAttributesToView(@NotNull ViewInfo viewInfo) {
    View cachedView = (View)viewInfo.getViewObject();
    myCachedView = new WeakReference<>(cachedView);

    if (cachedView != null) {
      // If the value is null, means that the attribute was reset to the default value. In that case, since this is a new view object
      // we do not need to propagate that change.
      myPendingAttributes.values().stream()
        .filter(Objects::nonNull)
        .forEach(pendingAttribute -> applyAttributeToView(pendingAttribute, viewInfo, myModel));
      hasPendingRelayout = true;
    }
  }

  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String name, @Nullable String value) {
    myLock.writeLock().lock();
    try {
      assert isValid;

      String key = attributeKey(namespace, name);
      PendingAttribute attribute = myPendingAttributes.get(key);
      boolean modified = true;
      if (attribute != null) {
        if (StringUtil.equals(attribute.value, value)) {
          // No change. We do not need to propagate the attribute value to the view
          modified = false;
        }
        else {
          attribute.value = value;
        }
      }
      else {
        attribute = new PendingAttribute(namespace, name, value);
        myPendingAttributes.put(key, attribute);
      }

      ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(myComponent);
      if (viewInfo != null) {
        View cachedView = myCachedView.get();
        if (cachedView == viewInfo.getViewObject()) {
          // We still have the same view info so we can just apply the delta (the passed attribute)
          if (modified && cachedView != null) {
            applyAttributeToView(attribute, viewInfo, myModel);
          }
        }
        else {
          // The view object has changed so we need to re-apply all the attributes
          applyAllPendingAttributesToView(viewInfo);
        }
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  @Override
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    myLock.readLock().lock();
    try {
      assert isValid;

      PendingAttribute pendingAttribute = myPendingAttributes.get(attributeKey(namespace, attribute));
      if (pendingAttribute != null) {
        return pendingAttribute.value;
      }

      // There are no pending modifications so read directly from the component
      return myComponent.getAttribute(namespace, attribute);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  private boolean finishTransaction() {
    assert isValid;
    isValid = false;

    myComponent.myCurrentTransaction = null;
    boolean hadPendingChanges = !myPendingAttributes.isEmpty();
    myPendingAttributes.clear();
    myOriginalValues.clear();

    return hadPendingChanges;
  }

  /**
   * Apply the current transaction, without saving to XML
   * It will trigger a layout.
   */
  public void apply() {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(myComponent);
    if (hasPendingRelayout && viewInfo != null) {
      View currentView = (View)viewInfo.getViewObject();
      if (currentView != myCachedView.get()) {
        // The view has changed since the last update so re-apply everything
        applyAllPendingAttributesToView(NlComponentHelperKt.getViewInfo(myComponent));
      }
      triggerViewRelayout((View)NlComponentHelperKt.getViewInfo(myComponent).getViewObject());
    }
  }

  /**
   * Commits all the pending changes to the model. After this method has been called, no more writes or reads can be made from
   * this transaction.
   *
   * @return true if the XML was changed as result of this call
   */
  public boolean commit() {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(myComponent);
    if (hasPendingRelayout && viewInfo != null) {
      View currentView = (View)viewInfo.getViewObject();
      if (currentView != myCachedView.get()) {
        // The view has changed since the last update so re-apply everything
        applyAllPendingAttributesToView(NlComponentHelperKt.getViewInfo(myComponent));
      }
      triggerViewRelayout((View)NlComponentHelperKt.getViewInfo(myComponent).getViewObject());
    }

    myLock.writeLock().lock();
    try {
      assert isValid;

      if (!myComponent.getTag().isValid()) {
        return finishTransaction();
      }

      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        return ApplicationManager.getApplication().runWriteAction((Computable<Boolean>)this::commit);
      }

      boolean modified = false;
      for (PendingAttribute attribute : myPendingAttributes.values()) {
        String originalValue = myOriginalValues.get(attributeKey(attribute.namespace, attribute.name));
        String currentValue = myComponent.getAttribute(attribute.namespace, attribute.name);

        if (!StringUtil.equals(currentValue, attribute.value)) {
          // The value has changed from what's in the XML
          if (!StringUtil.equals(originalValue, currentValue)) {
            // The attribute value has changed since we started the transaction, deal with the conflict.
            if (StringUtil.isEmpty(attribute.value)) {
              // In this case, the attribute has changed and we are trying to remove it or set it to empty. We will ignore our removal and
              // leave the attribute with the modified value.
              continue;
            }
            else if (StringUtil.equals(originalValue, attribute.value)) {
              // The attribute has been modified without the change being send through this transaction. Leave the modified value.
              continue;
            }
          }

          modified = true;
          myComponent.setAttribute(attribute.namespace, attribute.name, attribute.value);
        }
      }

      isSuccessful = true;
      finishTransaction();
      return modified;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  /**
   * Returns whether this transaction has been completed (either {@link #commit()} or {@link #rollback()} have been called.
   */
  public boolean isComplete() {
    return !isValid;
  }

  /**
   * Returns if this transaction has completed successfully.
   */
  public boolean isSuccessful() {
    return isSuccessful;
  }

  /**
   * Rolls-back the pending changes. After this method has been called, no more writes or reads can be made from
   * this transaction.
   *
   * @return whether there were any pending changes or not.
   */
  public boolean rollback() {
    myLock.writeLock().lock();
    try {
      return finishTransaction();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  /**
   * An attribute that hasn't been committed to the XML yet.
   */
  private static class PendingAttribute {
    final String namespace;
    final String name;
    String value;

    private PendingAttribute(@Nullable String namespace, @NotNull String name, @Nullable String value) {
      this.namespace = namespace;
      this.name = name;
      this.value = value;
    }
  }
}
