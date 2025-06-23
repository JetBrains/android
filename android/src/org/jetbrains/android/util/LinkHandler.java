/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.jetbrains.android.util;

import com.android.tools.idea.rendering.RenderUtils;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.rendering.HtmlLinkManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import java.lang.ref.WeakReference;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link HyperlinkListener} that does not hold a reference to any of the inputs.
 * If the inputs are released, then this will ignore the link click and log a warning.
 */
public class LinkHandler implements HyperlinkListener {
    private static final Logger LOG = Logger.getInstance(LinkHandler.class);
    private final WeakReference<EditorDesignSurface> myEditorDesignSurfaceReference;
    private final WeakReference<Module> myModuleReference;
    private final WeakReference<PsiFile> mySourceFileReference;
    private final WeakReference<HtmlLinkManager> myLinkManagerReference;

    public LinkHandler(@NotNull HtmlLinkManager linkManager,
                       @Nullable EditorDesignSurface surface,
                       @NotNull Module module,
                       @NotNull PsiFile sourceFile) {
        myLinkManagerReference = new WeakReference<>(linkManager);
        myEditorDesignSurfaceReference = new WeakReference<>(surface);
        myModuleReference = new WeakReference<>(module);
        mySourceFileReference = new WeakReference<>(sourceFile);
    }

    public void forceUserRequestedRefresh() {
        EditorDesignSurface surface = myEditorDesignSurfaceReference.get();
        if (surface != null) {
            RenderUtils.clearCache(surface.getConfigurations());
            surface.forceUserRequestedRefresh();
        }
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
            HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
            HTMLDocument doc = (HTMLDocument)pane.getDocument();
            doc.processHTMLFrameHyperlinkEvent(evt);
            return;
        }
        HtmlLinkManager linkManager = myLinkManagerReference.get();
        if (linkManager == null) {
            LOG.warn("HtmlLinkManager has been collected. Click will be ignored");
            return;
        }
        Module module = myModuleReference.get();
        if (module == null) {
            LOG.warn("Module has been collected. Click will be ignored");
            return;
        }
        if (module.isDisposed()) {
            LOG.warn("Module has been disposed. Click will be ignored");
            return;
        }
        PsiFile sourceFile = mySourceFileReference.get();
        if (sourceFile == null) {
            LOG.warn("PsiFile has been collected. Click will be ignored");
            return;
        }

        linkManager.handleUrl(e.getDescription(), module, sourceFile, true, new HtmlLinkManager.RefreshableSurface() {
            @Override
            public void handleRefreshRenderUrl() {
                forceUserRequestedRefresh();
            }

            @Override
            public void requestRender() {
                forceUserRequestedRefresh();
            }
        });
    }
}