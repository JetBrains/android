/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ColorEditor extends TypedCellEditor<EditedStyleItem, AttributeEditorValue> {
  private static final Logger LOG = Logger.getInstance(ColorEditor.class);

  private final ThemeEditorContext myContext;
  private final ColorComponent myComponent;
  private AttributeEditorValue myEditorValue = null;

  private final AndroidThemePreviewPanel myPreviewPanel;

  private EditedStyleItem myItem;

  public ColorEditor(@NotNull ThemeEditorContext context, AndroidThemePreviewPanel previewPanel) {
    myContext = context;

    myComponent = new ColorComponent();
    myComponent.addActionListener(new ColorEditorActionListener());

    myPreviewPanel = previewPanel;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myItem = value;
    ResourceResolver resourceResolver = myContext.getResourceResolver();
    assert resourceResolver != null;
    final List<Color> colors = ResourceHelper.resolveMultipleColors(resourceResolver, myItem.getItemResourceValue());
    myComponent.configure(myItem, colors);
    myEditorValue = null; // invalidate stored editor value

    return myComponent;
  }

  @Override
  public AttributeEditorValue getEditorValue() {
    return myEditorValue;
  }

  private class ColorEditorActionListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      String itemValue = myItem.getValue();
      final String colorName;
      // If it points to an existing resource.
      if (!SdkConstants.NULL_RESOURCE.equals(itemValue) &&
          itemValue.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        // Use the name of that resource.
        colorName = itemValue.substring(itemValue.indexOf('/') + 1);
      }
      else {
        // Otherwise use the name of the attribute.
        colorName = myItem.getName();
      }

      // TODO we need to handle color state lists correctly here.
      ResourceResolver resourceResolver = myContext.getResourceResolver();
      assert resourceResolver != null;
      String resolvedColor = ResourceHelper.colorToString(
        ResourceHelper.resolveColor(resourceResolver, myItem.getItemResourceValue()));

      final ChooseResourceDialog dialog = new ChooseResourceDialog(myContext.getCurrentThemeModule(), ChooseResourceDialog.COLOR_TYPES, resolvedColor, null,
                                                                   ChooseResourceDialog.ResourceNameVisibility.FORCE, colorName);

      final String oldValue = myItem.getItemResourceValue().getValue();

      final ScheduledExecutorService changeScheduler = Executors.newSingleThreadScheduledExecutor();
      final AtomicReference<ScheduledFuture> nextTimer = new AtomicReference<ScheduledFuture>();
      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          // We make the change in a separate thread and delay it by 150ms to prevent the dialog from
          // blocking and issuing a change and a repaint for every mouse movement.
          // TODO: This is a temporary workaround to the problem of paintComponent in the AndroidPreviewPanel being a slow operation.
          //       Remove once the inflating of the layout has been removed from the paintComponent.
          ScheduledFuture previousTimer = nextTimer.getAndSet(changeScheduler.schedule(new Runnable() {
            @Override
            public void run() {
              try {
                SwingUtilities.invokeAndWait(new Runnable() {
                  @Override
                  public void run() {
                    myItem.getItemResourceValue().setValue(resource == null ? oldValue : resource);
                    myPreviewPanel.invalidateGraphicsRenderer();
                  }
                });
              }
              catch (InterruptedException e1) {
                // Timer cancelled. We just ignore the latest change.
              }
              catch (InvocationTargetException e1) {
                LOG.error("Exception updating resource", e1);
              }
            }
          }, 150, TimeUnit.MILLISECONDS));

          if (previousTimer != null) {
            previousTimer.cancel(true);
          }
        }
      });

      dialog.show();

      changeScheduler.shutdown();

      // Restore the old value in the properties model
      myItem.getItemResourceValue().setValue(oldValue);

      myEditorValue = null;
      if (dialog.isOK()) {
        String value = dialog.getResourceName();
        if (value != null) {
          myEditorValue = new AttributeEditorValue(dialog.getResourceName(), dialog.overwriteResource());
        }
      } else {
        // User cancelled, clean up the preview
        myPreviewPanel.invalidateGraphicsRenderer();
      }

      if (myEditorValue == null) {
        ColorEditor.this.cancelCellEditing();
      } else {
        ColorEditor.this.stopCellEditing();
      }
    }
  }
}
