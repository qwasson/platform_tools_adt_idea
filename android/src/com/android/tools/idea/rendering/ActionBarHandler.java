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

package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.ManifestInfo.ActivityAttributes;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

/**
 * A callback to provide information related to the Action Bar as required by the
 * {@link LayoutLibrary}
 */
public class ActionBarHandler extends ActionBarCallback {
  private static final String ON_CREATE_OPTIONS_MENU = "onCreateOptionsMenu";                   //$NON-NLS-1$
  private static final String ATTR_MENU = "menu";                                               //$NON-NLS-1$
  private static final String ATTR_NAV_MODE = "actionBarNavMode";                               //$NON-NLS-1$
  private static final Pattern MENU_FIELD_PATTERN = Pattern.compile("R\\.menu\\.([a-z0-9_]+)"); //$NON-NLS-1$

  // The attribute values for ATTR_NAV_MODE.
  private static final String VALUE_NAV_MODE_TABS = "tabs";                                     //$NON-NLS-1$
  private static final String VALUE_NAV_MODE_LIST = "list";                                     //$NON-NLS-1$

  private final Object myCredential;
  @NotNull
  private RenderService myRenderService;
  @Nullable
  private List<String> myMenus;

  ActionBarHandler(@NotNull RenderService renderService, @Nullable Object credential) {
    myRenderService = renderService;
    myCredential = credential;
  }

  @Override
  public boolean getSplitActionBarWhenNarrow() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null) {
      return VALUE_SPLIT_ACTION_BAR_WHEN_NARROW.equals(attributes.getUiOptions());
    }
    return false;
  }

  // TODO: Handle this per file instead
  /** Flag which controls whether we should be showing the menu */
  private static boolean ourShowMenu = false;

  public static boolean isShowingMenu(@SuppressWarnings("UnusedParameters") @Nullable RenderContext context) {
    return ourShowMenu;
  }

  public static boolean showMenu(boolean showMenu, @Nullable RenderContext context, boolean repaint) {
    if (showMenu != ourShowMenu) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourShowMenu = showMenu;
      if (context != null && repaint) {
        context.requestRender();
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean isOverflowPopupNeeded() {
    return ourShowMenu || ResourceHelper.getFolderType(myRenderService.getPsiFile()) == ResourceFolderType.MENU;
  }

  @Override
  public List<String> getMenuIdNames() {
    if (myMenus != null) {
      return myMenus;
    }

    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      final XmlFile xmlFile = myRenderService.getPsiFile();
      String commaSeparatedMenus = AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_MENU, TOOLS_URI);
      if (commaSeparatedMenus != null) {
        myMenus = new ArrayList<String>();
        Iterables.addAll(myMenus, Splitter.on(',').trimResults().omitEmptyStrings().split(commaSeparatedMenus));
      } else {
        String context = AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_CONTEXT, TOOLS_URI);
        if (context != null && !context.isEmpty()) {
          // Glance at the onCreateOptionsMenu of the associated context and use any menus found there.
          // This is just a simple textual search; we need to replace this with a proper model lookup.
          boolean startsWithDot = context.charAt(0) == '.';
          if (startsWithDot || context.indexOf('.') == -1) {
            // Prepend application package
            String pkg = ManifestInfo.get(myRenderService.getModule(), false).getPackage();
            context = startsWithDot ? pkg + context : pkg + '.' + context;
          }
          final String fqn = context;
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              Project project = xmlFile.getProject();
              PsiClass clz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
              if (clz != null) {
                for (PsiMethod method : clz.findMethodsByName(ON_CREATE_OPTIONS_MENU, true)) {
                  if (method instanceof PsiCompiledElement) {
                    continue;
                  }
                  String matchText = method.getText();
                  Matcher matcher = MENU_FIELD_PATTERN.matcher(matchText);
                  Set<String> menus = Sets.newTreeSet();
                  int index = 0;
                  while (true) {
                    if (matcher.find(index)) {
                      menus.add(matcher.group(1));
                      index = matcher.end();
                    } else {
                      break;
                    }
                  }
                  if (!menus.isEmpty()) {
                    myMenus = new ArrayList<String>(menus);
                  }
                }
              }
            }
          });
        }
      }

      if (myMenus == null) {
        myMenus = Collections.emptyList();
      }
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }

    return myMenus;
  }

  @Override
  public HomeButtonStyle getHomeButtonStyle() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null && attributes.getParentActivity() != null) {
      return HomeButtonStyle.SHOW_HOME_AS_UP;
    }
    return HomeButtonStyle.NONE;
  }

  @Override
  public int getNavigationMode() {
    XmlFile xmlFile = myRenderService.getPsiFile();
    String navMode = StringUtil.notNullize(AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_NAV_MODE, TOOLS_URI)).trim();
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_TABS)) {
      return NAVIGATION_MODE_TABS;
    }
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_LIST)) {
      return NAVIGATION_MODE_LIST;
    }
    return NAVIGATION_MODE_STANDARD;
  }

  public void setMenuIdNames(@Nullable List<String> menus) {
    myMenus = menus;
  }

  private @Nullable ActivityAttributes getActivityAttributes() {
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      ManifestInfo manifest = ManifestInfo.get(myRenderService.getModule(), false);
      String activity = StringUtil.notNullize(myRenderService.getConfiguration().getActivity());
      return manifest.getActivityAttributes(activity);
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }
}
