/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;

/**
 * Class containing an {@link NlComponent} attributes transaction. All the modifications in this transaction are not committed until the
 * {@link #commit()} method is called. If {@link #rollback()} is called, all the changes are discarded.
 * A transaction can be used safely from multiple threads.
 */
public class AttributesTransaction implements NlAttributesHolder {
  private final NlComponent myComponent;

  /** Lock that guards all the operations on the attributes below */
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final HashMap<String, PendingAttribute> myPendingAttributes = new HashMap<>();
  private boolean isValid = true;
  @NotNull private WeakReference<ViewInfo> myCachedViewInfo = new WeakReference<>(null);

  public AttributesTransaction(@NotNull NlComponent thisComponent) {
    myComponent = thisComponent;
  }

  @NotNull
  private static String attributeKey(@Nullable String namespace, @NotNull String attribute) {
    return String.format("%s:%s", namespace, attribute);
  }

  /**
   * Apply the given {@link PendingAttribute} to the passed {@link ViewInfo}
   */
  private static void applyAttributeToView(@NotNull PendingAttribute attribute, @NotNull ViewInfo viewInfo) {
    if (attribute.name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
      String value = attribute.value;
      // This is a layout param
      Object layoutParams = viewInfo.getLayoutParamsObject();
      if (layoutParams == null) {
        return;
      }

      // TODO: Apply pending attribute to the given view info.
      // TODO: What to do when removing the attribute?
      //System.out.printf("Set '%s' to '%s'\n", attribute.name, value);
    }
  }

  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String name, @Nullable String value) {
    myLock.writeLock().lock();
    try {
      assert isValid;

      String key = attributeKey(namespace, name);
      PendingAttribute attribute = myPendingAttributes.get(key);
      if (attribute != null) {
        attribute.value = value;
      }
      else {
        attribute = new PendingAttribute(namespace, name, value);
        myPendingAttributes.put(key, attribute);
      }

      ViewInfo cachedViewInfo = myCachedViewInfo.get();
      if (cachedViewInfo == myComponent.viewInfo) {
        // We still have the same view info so we can just apply the delta (the passed attribute)
        if (cachedViewInfo != null) {
          applyAttributeToView(attribute, cachedViewInfo);
        }
      }
      else {
        // The view object has changed so we need to re-apply all the attributes
        cachedViewInfo = myComponent.viewInfo;
        myCachedViewInfo = new WeakReference<>(cachedViewInfo);

        if (cachedViewInfo != null) {
          for (PendingAttribute pendingAttribute : myPendingAttributes.values()) {
            applyAttributeToView(pendingAttribute, cachedViewInfo);
          }
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

  private boolean finishTransaction() {
    assert isValid;
    isValid = false;

    myComponent.myCurrentTransaction = null;
    boolean hadPendingChanges = !myPendingAttributes.isEmpty();
    myPendingAttributes.clear();

    return !hadPendingChanges;
  }

  /**
   * Commits all the pending changes to the model. After this method has been called, no more writes or reads can be made from
   * this transaction.
   *
   * @return whether there were any pending changes or not.
   */
  public boolean commit() {
    myLock.writeLock().lock();
    try {
      assert isValid;

      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        ApplicationManager.getApplication().runWriteAction((Computable<Boolean>)this::commit);
      }

      for (PendingAttribute attribute : myPendingAttributes.values()) {
        String currentValue = myComponent.getAttribute(attribute.namespace, attribute.name);
        if (!StringUtil.equals(currentValue, attribute.value)) {
          myComponent.setAttribute(attribute.namespace, attribute.name, attribute.value);
        }
      }

      return finishTransaction();
    } finally {
      myLock.writeLock().unlock();
    }
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
    } finally {
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
