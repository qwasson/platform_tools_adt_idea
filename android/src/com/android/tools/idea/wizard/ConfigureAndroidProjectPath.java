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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.sdk.wizard.SmwOldApiDirectInstall;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SDK_DIR;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * A path to configure global details of a new Android project
 */
public class ConfigureAndroidProjectPath extends DynamicWizardPath {
  public static final Key<String> BUILD_TOOLS_VERSION_KEY = createKey(ATTR_BUILD_TOOLS_VERSION, WIZARD, String.class);
  public static final Key<String> SDK_HOME_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  public static final Key<List> INSTALL_REQUESTS_KEY = createKey("packagesToInstall", WIZARD, List.class);
  public static final JBColor ANDROID_NPW_TITLE_COLOR = new JBColor(0x689F38, 0xFFFFFF);
  public static final JBColor ANDROID_NPW_HEADER_COLOR = new JBColor(0x689F38, 0x356822);
  private static final Logger LOG = Logger.getInstance(ConfigureAndroidProjectPath.class);

  @NotNull
  private final Disposable myParentDisposable;

  public ConfigureAndroidProjectPath(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @Override
  protected void init() {
    putSdkDependentParams();

    addStep(new ConfigureAndroidProjectStep(myParentDisposable));
    addStep(new ConfigureFormFactorStep(myParentDisposable));
    addStep(new LicenseAgreementStep(myParentDisposable));
    addStep(new SmwOldApiDirectInstall(myParentDisposable));
  }

  @Override
  public boolean validate() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      setErrorHtml("<html>Your Android SDK is missing, out of date, or is missing templates. " +
                   "Please ensure you are using SDK version 22 or later.<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>");
      return false;
    } else {
      return true;
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Configure Android Project";
  }

  public void putSdkDependentParams() {
    final AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    BuildToolInfo buildTool = sdkData != null ? sdkData.getLatestBuildTool() : null;
    FullRevision minimumRequiredBuildToolVersion = FullRevision.parseRevision(SdkConstants.MIN_BUILD_TOOLS_VERSION);
    if (buildTool != null && buildTool.getRevision().compareTo(minimumRequiredBuildToolVersion) >= 0) {
      myState.put(BUILD_TOOLS_VERSION_KEY, buildTool.getRevision().toString());
    } else {
      // We need to install a new build tools version
      myState.listPush(INSTALL_REQUESTS_KEY, PkgDesc.Builder.newBuildTool(minimumRequiredBuildToolVersion).create());
      myState.put(BUILD_TOOLS_VERSION_KEY, minimumRequiredBuildToolVersion.toString());
    }

    if (sdkData != null) {
      // Gradle expects a platform-neutral path
      myState.put(SDK_HOME_KEY, FileUtil.toSystemIndependentName(sdkData.getPath()));
    }
  }

  @Override
  public boolean performFinishingActions() {
    String projectLocation = myState.get(ConfigureAndroidProjectStep.PROJECT_LOCATION_KEY);
    if (projectLocation != null) {
      try {
        VirtualFile vf = VfsUtil.createDirectories(projectLocation);
        File projectRoot = VfsUtilCore.virtualToIoFile(vf);
        Template projectTemplate = Template.createFromName(CATEGORY_PROJECTS, NewProjectWizardState.PROJECT_TEMPLATE_NAME);
        projectTemplate.render(projectRoot, projectRoot, myState.flatten());
        try {
          NewProjectWizard.setGradleWrapperExecutable(projectRoot);
        }
        catch (IOException e) {
          LOG.error(e);
          return false;
        }
        return true;
      }
      catch (IOException e) {
        // TODO: Complain
      }
    }
    return false;
  }

  protected static JPanel buildConfigurationHeader() {
    JPanel panel = new JPanel();
    panel.setBackground(ANDROID_NPW_HEADER_COLOR);
    panel.setBorder(BorderFactory.createLineBorder(Color.RED));
    panel.setLayout(new GridLayoutManager(2, 2, new Insets(18, 0, 12, 0), 2, 2));
    GridConstraints c = new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_NORTHWEST,
                                            GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(60, 60), null);
    ImageComponent image = new ImageComponent(AndroidIcons.Wizards.NewProjectMascotGreen);
    panel.add(image, c);
    c = new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_HORIZONTAL,
                            GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
                            GridConstraints.SIZEPOLICY_FIXED, null, null, null);
    JLabel projectLabel = new JLabel("New Project");
    projectLabel.setForeground(Color.WHITE);
    projectLabel.setFont(projectLabel.getFont().deriveFont(24f));
    panel.add(projectLabel, c);
    c.setRow(1);
    c.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
    JLabel productLabel = new JLabel("Android Studio");
    productLabel.setForeground(Color.WHITE);
    panel.add(productLabel, c);
    return panel;
  }
}
