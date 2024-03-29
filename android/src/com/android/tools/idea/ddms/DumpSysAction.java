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
package com.android.tools.idea.ddms;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.Client;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link DumpSysAction} is a helper class that does the following when {@link #performAction()} is invoked:
 *  <ol>
 *    <li>Issues a <code>dumpsys [service] [package]</code> command on the device.</li>
 *    <li>Displays a progress bar that allows the user to cancel the shell command if necessary.</li>
 *    <li>On completion, saves the output of the command in a temporary file, and displays it in an editor.</li>
 *  </ol>
 */
public class DumpSysAction {
  private static final String TITLE = "Dump System Information";

  private final Project myProject;
  private final IDevice myDevice;
  private final String myService;
  private final Client myClient;

  @GuardedBy("this")
  private VirtualFile myOutputFile;

  public DumpSysAction(@NotNull Project p, @NotNull IDevice device, @NotNull String service, @Nullable Client client) {
    myProject = p;
    myDevice = device;
    myService = service;
    myClient = client;
  }

  public void performAction() {
    final CountDownLatch latch = new CountDownLatch(1);
    final CollectingOutputReceiver receiver = new CollectingOutputReceiver();

    String description = myClient == null ? null : myClient.getClientData().getClientDescription();
    final String pkgName = description != null ? description : "";
    final String command = String.format(Locale.US, "dumpsys %1$s %2$s", myService, pkgName).trim();

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              runShellCommand();
            } finally {
              latch.countDown();
            }
          }

          private void runShellCommand() {
            try {
              myDevice.executeShellCommand(command, receiver, 0, null);
            }
            catch (Exception e) {
              showError(myProject, "Unexpected error while obtaining system information", e);
              return;
            }

            try {
              String fileName = "dumpsys-" + pkgName;
              File f = FileUtil.createTempFile(fileName, ".txt", true);
              FileUtil.writeToFile(f, receiver.getOutput());
              //noinspection ResultOfMethodCallIgnored
              f.setReadOnly();
              setOutputFile(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f));
            }
            catch (IOException e) {
              showError(myProject, "Unexpected error while saving system information", e);
              return;
            }
          }
        });
      }
    }, ModalityState.defaultModalityState());

    new ShellTask(myProject, latch, receiver).queue();
  }

  private synchronized void setOutputFile(@Nullable VirtualFile f) {
    myOutputFile = f;
  }

  private synchronized VirtualFile getOutputFile() {
    return myOutputFile;
  }

  private static void showError(@Nullable final Project project, @NotNull final String message, @Nullable final Throwable throwable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String msg = message;
        if (throwable != null) {
          msg += throwable.getLocalizedMessage() != null ? ": " + throwable.getLocalizedMessage() : "";
        }

        Messages.showErrorDialog(project, msg, TITLE);
      }
    });
  }

  private class ShellTask extends Task.Modal {
    private final CountDownLatch myCompletionLatch;
    private final CollectingOutputReceiver myReceiver;

    public ShellTask(@NotNull Project project, CountDownLatch completionLatch, CollectingOutputReceiver receiver) {
      super(project, TITLE, true);

      myCompletionLatch = completionLatch;
      myReceiver = receiver;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);

      while (true) {
        try {
          if (myCompletionLatch.await(1, TimeUnit.SECONDS)) {
            break;
          }

          if (indicator.isCanceled()) {
            myReceiver.cancel();
          }
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    @Override
    public void onSuccess() {
      VirtualFile vf = getOutputFile();
      if (vf != null && myProject != null) {
        FileEditorManager.getInstance(myProject).openFile(vf, true);
      }
    }
  }
}
