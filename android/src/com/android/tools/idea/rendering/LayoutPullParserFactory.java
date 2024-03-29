/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.legacy.ILegacyPullParser;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.api.SessionParams.RenderingMode.V_SCROLL;

/**
 * The {@linkplain LayoutPullParserFactory} is responsible for creating
 * layout pull parsers for various different types of files.
 */
public class LayoutPullParserFactory {
  static final boolean DEBUG = false;
  private static final String TAG_APPWIDGET_PROVIDER = "appwidget-provider";
  private static final String TAG_PREFERENCE_SCREEN = "PreferenceScreen";

  public static boolean isSupported(PsiFile file) {
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    if (folderType == null) {
      return false;
    }
    switch (folderType) {
      case LAYOUT:
      case DRAWABLE:
      case MENU:
        return true;
      case XML:
        if (file instanceof XmlFile) {
          XmlTag rootTag = ((XmlFile)file).getRootTag();
          if (rootTag != null) {
            String tag = rootTag.getName();
            return tag.equals(TAG_APPWIDGET_PROVIDER);
          }
        }
        return false;
      default:
        return false;
    }
  }

  @Nullable
  public static ILayoutPullParser create(@NotNull final RenderService renderService) {
    final ResourceFolderType folderType = renderService.getFolderType();
    if (folderType == null) {
      return null;
    }

    if ((folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MENU || folderType == ResourceFolderType.XML)
        && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<ILayoutPullParser>() {
        @Nullable
        @Override
        public ILayoutPullParser compute() {
          return create(renderService);
        }
      });
    }

    XmlFile file = renderService.getPsiFile();

    // IntelliJ bug: Claims that folderType can be null below. Suppressed.
    //noinspection ConstantConditions
    switch (folderType) {
      case LAYOUT: {
        RenderLogger logger = renderService.getLogger();
        Set<XmlTag> expandNodes = renderService.getExpandNodes();
        HardwareConfig hardwareConfig = renderService.getHardwareConfigHelper().getConfig();
        return LayoutPsiPullParser.create(file, logger, expandNodes, hardwareConfig.getDensity());
      }
      case DRAWABLE:
        renderService.setDecorations(false);
        return createDrawableParser(file);
      case MENU:
        if (renderService.supportsCapability(Capability.ACTION_BAR)) {
          return new MenuLayoutParserFactory(renderService).render();
        }
        renderService.setRenderingMode(V_SCROLL);
        renderService.setDecorations(false);
        return new MenuPreviewRenderer(renderService, file).render();
      case XML: {
        // Switch on root type
        XmlTag rootTag = file.getRootTag();
        if (rootTag != null) {
          String tag = rootTag.getName();
          if (tag.equals(TAG_APPWIDGET_PROVIDER)) {
            // Widget
            renderService.setDecorations(false);
            return createWidgetParser(rootTag);
          } else if (tag.equals(TAG_PREFERENCE_SCREEN)) {
            // Preferences: TODO
          }
        }
        return null;

      }
      default:
        // Should have been prevented by isSupported(PsiFile)
        assert false : folderType;
        return null;
    }
  }

  private static ILegacyPullParser createDrawableParser(XmlFile file) {
    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element imageView = addRootElement(document, IMAGE_VIEW);
    setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    setAndroidAttr(imageView, ATTR_SRC, DRAWABLE_PREFIX + ResourceHelper.getResourceName(file));

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    // Allow tools:background in drawable XML files to manually set the render background.
    // Useful for example when dealing with vectors or shapes where the color happens to
    // be close to the IDE default background.
    String background = AndroidPsiUtils.getRootTagAttributeSafely(file, ATTR_BACKGROUND, TOOLS_URI);
    if (background != null && !background.isEmpty()) {
      setAndroidAttr(imageView, ATTR_BACKGROUND, background);
    }

    return new DomPullParser(document.getDocumentElement());
  }

  @Nullable
  private static ILayoutPullParser createWidgetParser(XmlTag rootTag) {
    // See http://developer.android.com/guide/topics/appwidgets/index.html:

    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    String layout = rootTag.getAttributeValue("initialLayout", ANDROID_URI);
    String preview = rootTag.getAttributeValue("previewImage", ANDROID_URI);
    if (layout == null && preview == null) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, layout != null ? VIEW_INCLUDE : IMAGE_VIEW);
    if (layout != null) {
      root.setAttribute(ATTR_LAYOUT, layout);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    } else {
      root.setAttribute(ATTR_SRC, preview);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    return new DomPullParser(document.getDocumentElement());
  }

  public static boolean needSave(@Nullable ResourceFolderType type) {
    // Only layouts are delegates to the IProjectCallback#getParser where we can supply a
    // parser directly from the live document; others read contents from disk via layoutlib.
    // TODO: Work on adding layoutlib support for this.
    return type != ResourceFolderType.LAYOUT;
  }

  public static void saveFileIfNecessary(PsiFile psiFile) {
    if (!needSave(ResourceHelper.getFolderType(psiFile.getVirtualFile()))) { // Avoid need for read lock in get parent
      return;
    }

    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return;
    }

    final FileDocumentManager fileManager = FileDocumentManager.getInstance();
    if (!fileManager.isFileModified(file)) {
      return;
    }

    final com.intellij.openapi.editor.Document document;
    document = fileManager.getCachedDocument(file);
    if (document == null || !fileManager.isDocumentUnsaved(document)) {
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            fileManager.saveDocument(document);
          }
        });
      }
    }, ModalityState.any());
  }

  protected static Element addRootElement(@NotNull Document document, @NotNull String tag) {
    Element root = document.createElement(tag);

    //root.setAttribute(XMLNS_ANDROID, ANDROID_URI);

    // Set up a proper name space
    Attr attr = document.createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    attr.setValue(ANDROID_URI);
    root.getAttributes().setNamedItemNS(attr);

    document.appendChild(root);
    return root;
  }

  protected static Element setAndroidAttr(Element element, String name, String value) {
    element.setAttributeNS(ANDROID_URI, name, value);
    //element.setAttribute(ANDROID_NS_NAME + ':' + name, value);
    //Attr attr = element.getOwnerDocument().createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    //attr.setValue(ANDROID_URI);
    //root.getAttributes().setNamedItemNS(attr);

    return element;
  }

  protected static ILegacyPullParser createEmptyParser() {
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, FRAME_LAYOUT);
    setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    return new DomPullParser(document.getDocumentElement());
  }
}
