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

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.AddAndroidActivityPath.KEY_SELECTED_TEMPLATE;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PACKAGE_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;
import static com.android.tools.idea.wizard.ConfigureFormFactorStep.NUM_ENABLED_FORM_FACTORS_KEY;
import static com.android.tools.idea.wizard.NewModuleWizardState.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Module creation for a given form factor
 */
public class NewFormFactorModulePath extends DynamicWizardPath {
  private static final Logger LOG = Logger.getInstance(NewFormFactorModulePath.class);
  private static final Key<Boolean> CREATE_ACTIVITY_KEY = createKey(ATTR_CREATE_ACTIVITY, PATH, Boolean.class);

  private static final Key<String> MODULE_LOCATION_KEY = createKey(ATTR_PROJECT_OUT, PATH, String.class);
  private static final Key<String> RES_DIR_KEY = createKey(ATTR_RES_DIR, PATH, String.class);
  private static final Key<String> SRC_DIR_KEY = createKey(ATTR_SRC_DIR, PATH, String.class);
  private static final Key<String> AIDL_DIR_KEY = createKey(ATTR_AIDL_DIR, PATH, String.class);
  private static final Key<String> MANIFEST_DIR_KEY = createKey(ATTR_MANIFEST_DIR, PATH, String.class);
  private static final Key<String> TEST_DIR_KEY = createKey(ATTR_TEST_DIR, PATH, String.class);

  private static final Key<String> RES_OUT_KEY = createKey(ATTR_RES_OUT, PATH, String.class);
  private static final Key<String> SRC_OUT_KEY = createKey(ATTR_SRC_OUT, PATH, String.class);
  private static final Key<String> AIDL_OUT_KEY = createKey(ATTR_AIDL_OUT, PATH, String.class);
  private static final Key<String> MANIFEST_OUT_KEY = createKey(ATTR_MANIFEST_OUT, PATH, String.class);
  private static final Key<String> TEST_OUT_KEY = createKey(ATTR_TEST_OUT, PATH, String.class);

  private static final Key<String> RELATIVE_PACKAGE_KEY = createKey(ATTR_RELATIVE_PACKAGE, PATH, String.class);
  private static final String RELATIVE_SRC_ROOT = FileUtil.join(TemplateWizard.MAIN_FLAVOR_SOURCE_PATH, TemplateWizard.JAVA_SOURCE_PATH);
  private static final String RELATIVE_TEST_ROOT = FileUtil.join(TemplateWizard.TEST_SOURCE_PATH, TemplateWizard.JAVA_SOURCE_PATH);

  private FormFactorUtils.FormFactor myFormFactor;
  private File myTemplateFile;
  private Disposable myDisposable;
  private final Key<Boolean> myIsIncludedKey;
  private final Key<String> myModuleNameKey;
  private TemplateParameterStep2 myParameterStep;
  private List<File> myFilesToOpen = Lists.newArrayList();

  public static List<NewFormFactorModulePath> getAvailableFormFactorModulePaths(@NotNull Disposable disposable) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    List<NewFormFactorModulePath> toReturn = Lists.newArrayList();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactorUtils.FormFactor formFactor = FormFactorUtils.FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      NewFormFactorModulePath path = new NewFormFactorModulePath(formFactor, templateFile, disposable);
      toReturn.add(path);
    }
    return toReturn;
  }

  public NewFormFactorModulePath(@NotNull FormFactorUtils.FormFactor formFactor, @NotNull File templateFile, @NotNull Disposable disposable) {
    myFormFactor = formFactor;
    myTemplateFile = templateFile;
    myDisposable = disposable;
    myIsIncludedKey = FormFactorUtils.getInclusionKey(formFactor);
    myModuleNameKey = FormFactorUtils.getModuleNameKey(formFactor);
  }

  @Override
  protected void init() {
    myState.put(WizardConstants.IS_LIBRARY_KEY, false);
    myState.put(SRC_DIR_KEY, calculateSrcDir());
    myState.put(RES_DIR_KEY, "src/main/res");
    myState.put(AIDL_DIR_KEY, "src/main/aidl");
    myState.put(MANIFEST_DIR_KEY, "src/main");
    myState.put(TEST_DIR_KEY, "src/androidTest");
    myState.put(CREATE_ACTIVITY_KEY, false);
    myState.put(RELATIVE_PACKAGE_KEY, "");

    addStep(new ActivityGalleryStep(myFormFactor, true, KEY_SELECTED_TEMPLATE, myDisposable));

    Object packageName = myState.get(PACKAGE_NAME_KEY);
    if (packageName == null) {
      packageName = "";
    }
    Map<String, Object> presetsMap = ImmutableMap
      .of(PACKAGE_NAME_KEY.name, packageName, TemplateMetadata.ATTR_IS_LAUNCHER, true, TemplateMetadata.ATTR_PARENT_ACTIVITY_CLASS, "");
    myParameterStep = new TemplateParameterStep2(myFormFactor, presetsMap, myDisposable, PACKAGE_NAME_KEY, new SourceProvider[0]);
    addStep(myParameterStep);
  }

  @Override
  public void onPathStarted(boolean fromBeginning) {
    super.onPathStarted(fromBeginning);
    // TODO: Refactor handling of presets in TemplateParameterStep2 so that this isn't necessary
    myParameterStep.setPresetValue(PACKAGE_NAME_KEY.name, myState.get(PACKAGE_NAME_KEY));

    Set<Key> keys = Sets.newHashSetWithExpectedSize(5);
    keys.add(PACKAGE_NAME_KEY);
    keys.add(SRC_DIR_KEY);
    keys.add(TEST_DIR_KEY);
    keys.add(PROJECT_LOCATION_KEY);
    keys.add(NUM_ENABLED_FORM_FACTORS_KEY);
    deriveValues(keys);
  }

  @NotNull
  private String calculateSrcDir() {
    String packageSegment = myState.get(PACKAGE_NAME_KEY);
    if (packageSegment == null) {
      packageSegment = "";
    } else {
      packageSegment = packageSegment.replace('.', File.separatorChar);
    }
    return FileUtil.join(RELATIVE_SRC_ROOT, packageSegment);
  }

  @NotNull
  private String calculateTestDir() {
    String packageSegment = myState.get(PACKAGE_NAME_KEY);
    if (packageSegment == null) {
      packageSegment = "";
    } else {
      packageSegment = packageSegment.replace('.', File.separatorChar);
    }
    return FileUtil.join(RELATIVE_TEST_ROOT, packageSegment);
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    if (modified.contains(NUM_ENABLED_FORM_FACTORS_KEY)) {
      //noinspection ConstantConditions
      if (myState.containsKey(NUM_ENABLED_FORM_FACTORS_KEY) && myState.get(NUM_ENABLED_FORM_FACTORS_KEY) == 1) {
        myState.put(myModuleNameKey, "app");
      }
      else if (!myState.containsKey(myModuleNameKey)) {
        myState.put(myModuleNameKey, FormFactorUtils.getModuleName(myFormFactor));
      }
    }
    boolean basePathModified = modified.contains(PROJECT_LOCATION_KEY) || modified.contains(myModuleNameKey);
    if (basePathModified) {
      myState.put(MODULE_LOCATION_KEY, FileUtil.join(myState.get(PROJECT_LOCATION_KEY), myState.get(myModuleNameKey)));
    }
    if (modified.contains(SRC_DIR_KEY) || modified.contains(PACKAGE_NAME_KEY)) {
      myState.put(SRC_DIR_KEY, calculateSrcDir());
    }
    if (modified.contains(TEST_DIR_KEY) || modified.contains(PACKAGE_NAME_KEY)) {
      myState.put(TEST_DIR_KEY, calculateTestDir());
    }
    if (modified.contains(SRC_DIR_KEY) || basePathModified) {
      updateOutputPath(SRC_DIR_KEY, SRC_OUT_KEY);
    }
    if (modified.contains(RES_DIR_KEY) || basePathModified) {
      updateOutputPath(RES_DIR_KEY, RES_OUT_KEY);
    }
    if (modified.contains(AIDL_DIR_KEY) || basePathModified) {
      updateOutputPath(AIDL_DIR_KEY, AIDL_OUT_KEY);
    }
    if (modified.contains(MANIFEST_DIR_KEY) || basePathModified) {
      updateOutputPath(MANIFEST_DIR_KEY, MANIFEST_OUT_KEY);
    }
    if (modified.contains(TEST_DIR_KEY) || basePathModified) {
      updateOutputPath(TEST_DIR_KEY, TEST_OUT_KEY);
    }
    if (myState.containsKey(NEWLY_INSTALLED_API_KEY)) {
      Integer newApiLevel = myState.get(NEWLY_INSTALLED_API_KEY);
      Key<Integer> targetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(myFormFactor);
      Integer currentTargetLevel = myState.get(targetApiLevelKey);
      if (currentTargetLevel == null || newApiLevel > currentTargetLevel) {
        // If the newly installed is greater than the current target, we know we're not targeting
        // a preview version, so we can safely set build/target api levels to the newly installed level
        String newApiString = Integer.toString(newApiLevel);
        myState.put(targetApiLevelKey, newApiLevel);
        myState.put(FormFactorUtils.getTargetApiStringKey(myFormFactor), newApiString);
        myState.put(FormFactorUtils.getBuildApiLevelKey(myFormFactor), newApiLevel);
        myState.put(FormFactorUtils.getBuildApiKey(myFormFactor), newApiString);
      }
    }
  }

  private void updateOutputPath(@NotNull Key<String> relativeDirKey, @NotNull Key<String> outputDirKey) {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    String moduleName = myState.get(myModuleNameKey);
    String relativeLocation = myState.get(relativeDirKey);
    if (relativeLocation == null || projectLocation == null || moduleName == null) {
      return;
    }
    File baseLocation = new File(projectLocation, moduleName);
    relativeLocation = FileUtil.toSystemDependentName(relativeLocation);
    myState.put(outputDirKey, new File(baseLocation, relativeLocation).getPath());
  }

  @NotNull
  @Override
  public String getPathName() {
    return myFormFactor + " Module Creation Path";
  }

  @Override
  public boolean isPathVisible() {
    Boolean included = myState.get(myIsIncludedKey);
    return included == null ? false : included;
  }

  @Override
  public boolean performFinishingActions() {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    if (projectLocation != null) {
      File projectRoot = new File(projectLocation);
      File moduleRoot = new File(projectRoot, myState.get(myModuleNameKey));
      try {
        Template.checkedCreateDirectoryIfMissing(moduleRoot);
      }
      catch (IOException e) {
        LOG.error(e);
        return false;
      }

      Template template = Template.createFromPath(myTemplateFile);
      Map<String, Object> templateState = FormFactorUtils.scrubFormFactorPrefixes(myFormFactor, myState.flatten());
      template.render(projectRoot, moduleRoot, templateState);
      TemplateEntry templateEntry = myState.get(KEY_SELECTED_TEMPLATE);
      if (templateEntry == null) {
        return true;
      }
      Template activityTemplate = templateEntry.getTemplate();
      for (Parameter parameter : templateEntry.getMetadata().getParameters()) {
        templateState.put(parameter.id, myState.get(myParameterStep.getParameterKey(parameter)));
      }
      activityTemplate.render(projectRoot, moduleRoot, templateState);
      myFilesToOpen = activityTemplate.getFilesToOpen();
      return true;
    }
    else {
      return false;
    }
  }

  @NotNull
  public List<File> getFilesToOpen() {
    return myFilesToOpen;
  }
}
