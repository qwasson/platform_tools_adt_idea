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

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.FormFactorUtils.FormFactor;
import com.google.common.collect.Lists;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_JAVA_VERSION;
import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;

/**
 * Presents a wizard to the user to create a new project.
 */
public class NewProjectWizardDynamic extends DynamicWizard {
  private static final String ERROR_MSG_TITLE = "Error in New Project Wizard";
  private final List<File> myFilesToOpen = Lists.newArrayList();

  public NewProjectWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Project");
    setTitle("Create New Project");
  }

  @Override
  public void init() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg =
        "<html>Your Android SDK is missing, out of date, or is missing templates.<br>" +
        "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      super.init();
      Messages.showErrorDialog(msg, title);
    }
    addPaths();
    initState();
    super.init();
  }

  /**
   * Add the steps for this wizard
   */
  protected void addPaths() {
    addPath(new ConfigureAndroidProjectPath(getDisposable()));
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths(getDisposable())) {
      addPath(path);
    }
  }

  /**
   * Populate our state store with some common configuration items, such as the SDK location and the Gradle configuration.
   */
  protected void initState() {
    ScopedStateStore state = getState();
    state.put(WizardConstants.GRADLE_VERSION_KEY, GRADLE_LATEST_VERSION);
    state.put(WizardConstants.GRADLE_PLUGIN_VERSION_KEY, GRADLE_PLUGIN_RECOMMENDED_VERSION);
    state.put(WizardConstants.USE_PER_MODULE_REPOS_KEY, false);
    state.put(WizardConstants.IS_NEW_PROJECT_KEY, true);
    state.put(WizardConstants.IS_GRADLE_PROJECT_KEY, true);
    try {
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      LOG.error("Could not create default debug keystore: " + e.getMessage());
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, "");
    }
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData != null) {
      state.put(WizardConstants.SDK_DIR_KEY, sdkData.getLocation().getPath());
    }
    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    if (mavenUrl != null) {
      state.put(WizardConstants.MAVEN_URL_KEY, mavenUrl);
    }
  }

  @Override
  protected String getWizardActionDescription() {
    return String.format("Create %1$s", getState().get(APPLICATION_NAME_KEY));
  }

  @Override
  public void performFinishingActions() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
    String rootPath = getState().get(PROJECT_LOCATION_KEY);
    if (rootPath == null) {
      LOG.error("No root path specified for project");
      return;
    }
    File rootLocation = new File(rootPath);

    String projectName = getState().get(APPLICATION_NAME_KEY);
    if (projectName == null) {
      projectName = "Unnamed Project";
    }

    // Collect files to open
    for (AndroidStudioWizardPath path : myPaths) {
      if (path instanceof NewFormFactorModulePath) {
        myFilesToOpen.addAll(((NewFormFactorModulePath)path).getFilesToOpen());
      }
    }

    // Pick the highest language level of all the modules/form factors.
    // We have to pick the language level up front while creating the project rather than
    // just reacting to it during sync, because otherwise the user gets prompted with
    // a changing-language-level-requires-reopening modal dialog box and have to reload
    // the project
    LanguageLevel initialLanguageLevel = null;
    Iterator<FormFactor> iterator = FormFactor.iterator();
    while (iterator.hasNext()) {
      FormFactor factor = iterator.next();
      Object version = getState().get(FormFactorUtils.getLanguageLevelKey(factor));
      if (version != null) {
        LanguageLevel level = LanguageLevel.parse(version.toString());
        if (level != null && (initialLanguageLevel == null || level.isAtLeast(initialLanguageLevel))) {
          initialLanguageLevel = level;
        }
      }
    }

    try {
      projectImporter.importNewlyCreatedProject(projectName, rootLocation, new NewProjectImportGradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull final Project project) {
          // Open files -- but wait until the Android facets are available, otherwise for example
          // the layout editor won't add Design tabs to the file
          StartupManagerEx manager = StartupManagerEx.getInstanceEx(project);
          if (!manager.postStartupActivityPassed()) {
            manager.registerPostStartupActivity(new Runnable() {
              @Override
              public void run() {
                openTemplateFiles(project);
              }
            });
          }
          else {
            openTemplateFiles(project);
          }
        }

        private boolean openTemplateFiles(Project project) {
          return TemplateUtils.openEditors(project, myFilesToOpen, true);
        }
      }, null, initialLanguageLevel);
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
  }
}
