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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.sdk.DefaultSdks.isValidAndroidSdkPath;

public final class SdkSync {
  private static final String ERROR_DIALOG_TITLE = "Sync Android SDKs";

  private SdkSync() {
  }

  public static void syncIdeAndProjectAndroidHomes(@NotNull LocalProperties localProperties) {
    syncIdeAndProjectAndroidHomes(localProperties, new FindValidSdkPathTask());
  }

  @VisibleForTesting
  static void syncIdeAndProjectAndroidHomes(@NotNull final LocalProperties localProperties, @NotNull FindValidSdkPathTask findSdkPathTask) {
    if (localProperties.hasAndroidDirProperty()) {
      // if android.dir is specified, we don't sync SDKs. User is working with SDK sources.
      return;
    }

    final File androidHomePath = DefaultSdks.getDefaultAndroidHome();
    final File projectAndroidHomePath = localProperties.getAndroidSdkPath();

    if (androidHomePath != null) {
      if (projectAndroidHomePath == null) {
        // If we have the IDE default SDK and we don't have a project SDK, update local.properties with default SDK path and exit.
        setProjectSdk(localProperties, androidHomePath);
        return;
      }
      if (!isValidAndroidSdkPath(projectAndroidHomePath)) {
        // If we have the IDE default SDK and we don't have a valid project SDK, update local.properties with default SDK path and exit.
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              String format =
                "The path\n'%1$s'\ndoes not refer to an Android SDK.\n\nAndroid Studio will use its default SDK instead:\n'%2$s'\n" +
                "and will modify the project's local.properties file.";
              Messages.showErrorDialog(String.format(format, projectAndroidHomePath, androidHomePath.getPath()), ERROR_DIALOG_TITLE);
            }
            setProjectSdk(localProperties, androidHomePath);
          }
        });
        return;
      }
    }
    else {
      if (projectAndroidHomePath == null || !isValidAndroidSdkPath(projectAndroidHomePath)) {
        // We don't have any SDK (IDE or project.)
        File selectedPath = findSdkPathTask.selectValidSdkPath();
        if (selectedPath == null) {
          throw new ExternalSystemException("Unable to continue until an Android SDK is specified");
        }
        setIdeSdk(selectedPath, localProperties);
        return;
      }

      // If we have a valid project SDK but we don't have IDE default SDK, update IDE with project SDK path and exit.
      setIdeSdk(projectAndroidHomePath, localProperties);
      return;
    }

    if (!FileUtil.filesEqual(androidHomePath, projectAndroidHomePath)) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          // Prompt the user to choose between the SDK in the Studio and the one in local.properties.
          ChooseSdkPathDialog dialog = new ChooseSdkPathDialog(androidHomePath, projectAndroidHomePath);
          dialog.show();
          switch (dialog.getExitCode()) {
            case ChooseSdkPathDialog.USE_IDE_SDK_PATH:
              setProjectSdk(localProperties, androidHomePath);
              mergeIfNeeded(projectAndroidHomePath, androidHomePath);
              break;
            case ChooseSdkPathDialog.USE_PROJECT_SDK_PATH:
              setIdeSdk(projectAndroidHomePath, localProperties);
              mergeIfNeeded(androidHomePath, projectAndroidHomePath);
          }
        }
      });
    }
  }

  private static void setIdeSdk(@NotNull final File projectAndroidHomePath, @NotNull LocalProperties localProperties) {
    // There is one case where DefaultSdks.setDefaultAndroidHome will not update local.properties in the project. The conditions for this to
    // happen are:
    // 1. This is a fresh install of Android Studio and user does not set Android SDK
    // 2. User imports a project that does not have a local.properties file
    // Just to be on the safe side, we update local.properties.
    setProjectSdk(localProperties, projectAndroidHomePath);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            DefaultSdks.setDefaultAndroidHome(projectAndroidHomePath);
          }
        });
      }
    });
  }

  private static void setProjectSdk(@NotNull LocalProperties localProperties, @NotNull File androidHomePath) {
    if (FileUtil.filesEqual(localProperties.getAndroidSdkPath(), androidHomePath)) {
      return;
    }
    localProperties.setAndroidSdkPath(androidHomePath);
    try {
      localProperties.save();
    }
    catch (IOException e) {
      String msg = String.format("Unable to save '%1$s'", localProperties.getFilePath().getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  private static void mergeIfNeeded(@NotNull final File sourceSdk, @NotNull final File destSdk) {
    if (SdkMerger.hasMergeableContent(sourceSdk, destSdk)) {
      String msg = "The Android SDK at\n\n%1s\n\nhas packages not in your project's SDK at\n\n%2s\n\n" +
                   "Would you like to copy into the project SDK?";
      int result = MessageDialogBuilder.yesNo("Merge SDKs", String.format(msg, sourceSdk.getPath(), destSdk.getPath()))
        .yesText("Copy")
        .noText("Do not copy")
        .show();
      if (result == Messages.YES) {
        Task.Backgroundable task = new Task.Backgroundable(null, "Merging Android SDKs") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            SdkMerger.mergeSdks(sourceSdk, destSdk, indicator);
          }
        };
        ProgressManager.getInstance().run(task);
      }
    }
  }

  @VisibleForTesting
  static class FindValidSdkPathTask {
    @Nullable
    File selectValidSdkPath() {
      final AtomicReference<File> pathRef = new AtomicReference<File>();
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          findValidSdkPath(pathRef);
        }
      });
      return pathRef.get();
    }

    private static void findValidSdkPath(@NotNull AtomicReference<File> pathRef) {
      Sdk jdk = DefaultSdks.getDefaultJdk();
      String jdkPath = jdk != null ? jdk.getHomePath() : null;
      SelectSdkDialog dialog = new SelectSdkDialog(jdkPath, null);
      dialog.setModal(true);
      if (!dialog.showAndGet()) {
        String msg = "An Android SDK is needed to continue. Would you like to try again?";
        int result = Messages.showYesNoDialog(msg, ERROR_DIALOG_TITLE, null);
        if (result == Messages.YES) {
          findValidSdkPath(pathRef);
        }
        return;
      }
      final File path = new File(dialog.getAndroidHome());
      if (!isValidAndroidSdkPath(path)) {
        String format = "The path\n'%1$s'\ndoes not refer to a valid Android SDK. Would you like to try again?";
        int result = Messages.showYesNoDialog(String.format(format, path.getPath()), ERROR_DIALOG_TITLE, null);
        if (result == Messages.YES) {
          findValidSdkPath(pathRef);
        }
        return;
      }
      pathRef.set(path);
    }
  }
}
