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
package com.android.tools.idea.memory.actions;

import com.android.tools.idea.memory.MemoryMonitorView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CloseMemoryMonitorWindow extends AnAction {
  private MemoryMonitorView myView;

  public CloseMemoryMonitorWindow(MemoryMonitorView view) {
    super("Close session", "Closes the memory profiling session.", AllIcons.Actions.Cancel);
    myView = view;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myView.close();
  }
}
