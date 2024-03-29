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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk;
import static org.jetbrains.android.sdk.AndroidSdkUtils.findSuitableAndroidSdk;
import static org.jetbrains.android.sdk.AndroidSdkUtils.tryToCreateAndroidSdk;

/**
 * Sets an Android SDK to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer<IdeaAndroidProject> {
  private static final Logger LOG = Logger.getInstance(AndroidSdkModuleCustomizer.class);

  /**
   * Sets an Android SDK to the given module only if:
   * <ol>
   * <li>the given module was created by importing an {@code AndroidProject}</li>
   * <li>there is a matching Android SDK already defined in IDEA</li>
   * </ol>
   *
   * @param module         module to customize.
   * @param project        project that owns the module to customize.
   * @param androidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject) {
    if (androidProject == null) {
      return;
    }
    File androidSdkHomePath = DefaultSdks.getDefaultAndroidHome();
    // Android SDK may be not configured in IntelliJ
    if (androidSdkHomePath == null) {
      return;
    }

    String compileTarget = androidProject.getDelegate().getCompileTarget();

    Sdk sdk = findSuitableAndroidSdk(compileTarget);
    if (sdk == null) {
      sdk = tryToCreateAndroidSdk(androidSdkHomePath, compileTarget);
    }

    if (sdk != null) {
      setModuleSdk(module, sdk);
      return;
    }

    String text = String.format("Module '%1$s': platform '%2$s' not found.", module.getName(), compileTarget);
    LOG.info(text);

    Message msg = new Message(FAILED_TO_SET_UP_SDK, Message.Type.ERROR, text);
    ProjectSyncMessages.getInstance(project).add(msg);
  }
}
