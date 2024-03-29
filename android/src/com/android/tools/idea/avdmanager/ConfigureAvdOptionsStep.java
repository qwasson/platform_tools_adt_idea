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
package com.android.tools.idea.avdmanager;

import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.CameraLocation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.Storage;
import com.android.tools.idea.wizard.*;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

import static com.android.sdklib.devices.Storage.Unit;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Options panel for configuring various AVD options. Has an "advanced" mode and a "simple" mode.
 * Help and error messaging appears on the right hand side.
 */
public class ConfigureAvdOptionsStep extends DynamicWizardStepWithHeaderAndDescription {
  // CardLayout ids for switching between SD card types
  private static final String EXISTING_SDCARD = "existingsdcard";
  private static final String NEW_SDCARD = "newsdcard";
  private JBLabel myDeviceName;
  private JBLabel myDeviceDetails;
  private JButton myChangeDeviceButton;
  private JBLabel mySystemImageName;
  private JBLabel mySystemImageDetails;
  private JButton myChangeSystemImageButton;
  private TextFieldWithBrowseButton myExistingSdCard;
  private JPanel mySdCardSettings;
  private JComboBox myScalingComboBox;
  private ASGallery<ScreenOrientation> myOrientationToggle;
  private JPanel myRoot;
  private JCheckBox myUseHostGPUCheckBox;
  private JCheckBox myStoreASnapshotForCheckBox;
  private JComboBox myFrontCameraCombo;
  private JComboBox myBackCameraCombo;
  private JComboBox mySpeedCombo;
  private JComboBox myLatencyCombo;
  private JButton myShowAdvancedSettingsButton;
  private AvdConfigurationOptionHelpPanel myAvdConfigurationOptionHelpPanel;
  private JBLabel myToggleSdCardSettingsLabel;
  private JPanel myMemoryAndStoragePanel;
  private JPanel myStartupOptionsPanel;
  private JPanel myNetworkPanel;
  private JPanel myCameraPanel;
  private JPanel myPerformancePanel;
  private StorageField myRamStorage;
  private StorageField myVmHeapStorage;
  private StorageField myInternalStorage;
  private StorageField myNewSdCardStorage;
  private JBLabel myMemoryAndStorageLabel;
  private JBLabel myRamLabel;
  private JBLabel myVmHeapLabel;
  private JBLabel myInternalStorageLabel;
  private JBLabel mySdCardLabel;
  private JPanel mySkinPanel;
  private TextFieldWithBrowseButton myCustomSkinPath;
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private Set<JComponent> myAdvancedOptionsComponents;

  // Labels used for the advanced settings toggle button
  private static final String ADVANCED_SETTINGS = "Advanced Settings";
  private static final String SHOW = "Show " + ADVANCED_SETTINGS;
  private static final String HIDE = "Hide " + ADVANCED_SETTINGS;

  // Labels used for the SD card-type toggle link
  private static final String SWITCH_TO_NEW_SD_CARD = "Or create a new image...";
  private static final String SWITCH_TO_EXISTING_SD_CARD = "Or use an existing data file...";
  private Set<JComponent> myErrorStateComponents = Sets.newHashSet();

  // Intermediate key for storing the string path before we convert it to a file
  private static final Key<String> CUSTOM_SKIN_PATH_KEY = createKey(WIZARD_ONLY + "CustomSkinPath",
                                                                    ScopedStateStore.Scope.STEP, String.class);

  public ConfigureAvdOptionsStep(@Nullable Disposable parentDisposable) {
    super("Configure AVD", null, null, parentDisposable);
    myAvdConfigurationOptionHelpPanel.setPreferredSize(new Dimension(360, -1));
    setBodyComponent(myRoot);
    registerAdvancedOptionsVisibility();
    toggleAdvancedSettings(false);
    myToggleSdCardSettingsLabel.setText(SWITCH_TO_EXISTING_SD_CARD);
    myShowAdvancedSettingsButton.setText(SHOW);

    ActionListener toggleAdvancedSettingsListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myShowAdvancedSettingsButton.getText().equals(SHOW)) {
          toggleAdvancedSettings(true);
          myShowAdvancedSettingsButton.setText(HIDE);
        }
        else {
          toggleAdvancedSettings(false);
          myShowAdvancedSettingsButton.setText(SHOW);
        }
      }
    };
    myShowAdvancedSettingsButton.addActionListener(toggleAdvancedSettingsListener);
    myChangeDeviceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DynamicWizard wizard = getWizard();
        if (wizard != null) {
          wizard.navigateToNamedStep(CHOOSE_DEVICE_DEFINITION_STEP, false);
        }
      }
    });
    myChangeSystemImageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DynamicWizard wizard = getWizard();
        if (wizard != null) {
          wizard.navigateToNamedStep(CHOOSE_SYSTEM_IMAGE_STEP, false);
        }
      }
    });
    myToggleSdCardSettingsLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        toggleSdCardSettings(SWITCH_TO_EXISTING_SD_CARD.equals(myToggleSdCardSettingsLabel.getText()));
      }
    });
    myToggleSdCardSettingsLabel.setForeground(JBColor.blue);
  }

  /**
   * Toggle the SD card between using an existing file and creating a new file.
   */
  private void toggleSdCardSettings(boolean useExisting) {
    if (useExisting) {
      ((CardLayout)mySdCardSettings.getLayout()).show(mySdCardSettings, EXISTING_SDCARD);
      myToggleSdCardSettingsLabel.setText(SWITCH_TO_NEW_SD_CARD);
      myState.put(USE_EXISTING_SD_CARD, true);
    } else {
      ((CardLayout)mySdCardSettings.getLayout()).show(mySdCardSettings, NEW_SDCARD);
      myToggleSdCardSettingsLabel.setText(SWITCH_TO_EXISTING_SD_CARD);
      myState.put(USE_EXISTING_SD_CARD, false);
    }
  }

  @Override
  public void init() {
    super.init();
    registerComponents();
    deregister(getDescriptionText());
    getDescriptionText().setVisible(false);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    Device device = myState.get(DEVICE_DEFINITION_KEY);
    if (device != null) {
      toggleOptionals(device);
    }
    Boolean useExisting = myState.get(USE_EXISTING_SD_CARD);
    if (useExisting != null)
    toggleSdCardSettings(useExisting);
  }

  @Override
  public boolean validate() {
    clearErrorState();
    boolean valid = true;
    // Check Ram
    Storage ram = myState.get(RAM_STORAGE_KEY);
    if (ram == null || ram.getSizeAsUnit(Unit.MiB) < 128) {
      setErrorState("RAM must be a numeric (integer) value of at least 128Mb. Recommendation is 1Gb.",
                    myMemoryAndStorageLabel, myRamLabel, myRamStorage);
      valid = false;
    }

    // Check VM Heap
    Storage vmHeap = myState.get(VM_HEAP_STORAGE_KEY);
    if (vmHeap == null || vmHeap.getSizeAsUnit(Unit.MiB) < 16) {
      setErrorState("VM Heap must be a numeric (integer) value of at least 16Mb.",
                    myMemoryAndStorageLabel, myVmHeapLabel, myVmHeapStorage);
      valid = false;
    }

    // Check Internal Storage
    Storage internal = myState.get(INTERNAL_STORAGE_KEY);
    if (internal == null || internal.getSizeAsUnit(Unit.MiB) < 200) {
      setErrorState("Internal storage must be a numeric (integer) value of at least 200Mb.",
                    myMemoryAndStorageLabel, myInternalStorageLabel, myInternalStorage);
      valid = false;
    }

    // If we're using an existing SD card, make sure it exists
    Boolean useExistingSd = myState.get(USE_EXISTING_SD_CARD);
    if (useExistingSd != null && useExistingSd) {
      String path = myState.get(EXISTING_SD_LOCATION);
      if (path == null || !new File(path).isFile()) {
        setErrorState("The specified SD image file must be a valid image file",
                      myMemoryAndStorageLabel, mySdCardLabel, myExistingSdCard);
        valid = false;
      }
    } else {
      Storage sdCard = myState.get(SD_CARD_STORAGE_KEY);
      if (sdCard != null && (sdCard.getSizeAsUnit(Unit.MiB) < 30 || sdCard.getSizeAsUnit(Unit.GiB) > 1023)) {
        setErrorState("The SD card must be between 30Mb and 1023Gb",
                      myMemoryAndStorageLabel, mySdCardLabel, myNewSdCardStorage);
        valid = false;
      }
    }

    File skinDir = myState.get(CUSTOM_SKIN_FILE_KEY);
    if (skinDir != null) {
      File layoutFile = new File(skinDir, "layout");
      if (!layoutFile.isFile()) {
        setErrorHtml("The skin directory does not point to a valid skin.");
        valid = false;
      }
    }

    return valid;
  }

  /**
   * Clear the error highlighting around any components that had previously been marked as errors
   */
  private void clearErrorState() {
    for (JComponent c : myErrorStateComponents) {
      if (c instanceof JLabel) {
        c.setForeground(JBColor.foreground());
        ((JLabel)c).setIcon(null);
      } else if (c instanceof StorageField) {
        ((StorageField)c).setError(false);
      } else {
        c.setBorder(null);
      }
    }
    myAvdConfigurationOptionHelpPanel.setErrorMessage("");
  }

  /**
   * Set an error message and mark the given components as being in error state
   */
  private void setErrorState(String message, JComponent... errorComponents) {
    myAvdConfigurationOptionHelpPanel.setErrorMessage(message);
    for (JComponent c : errorComponents) {
      if (c instanceof JLabel) {
        c.setForeground(JBColor.RED);
        ((JLabel)c).setIcon(AllIcons.General.BalloonError);
      } else if (c instanceof StorageField) {
        ((StorageField)c).setError(true);
      } else {
        c.setBorder(new LineBorder(JBColor.RED));
      }
      myErrorStateComponents.add(c);
    }
  }

  /**
   * Bind components to their specified keys and help messaging.
   */
  private void registerComponents() {
    register(DEVICE_DEFINITION_KEY, myDeviceName, DEVICE_NAME_BINDING);
    register(DEVICE_DEFINITION_KEY, myDeviceDetails, DEVICE_DETAILS_BINDING);
    register(SYSTEM_IMAGE_KEY, mySystemImageName, SYSTEM_IMAGE_NAME_BINDING);
    register(SYSTEM_IMAGE_KEY, mySystemImageDetails, SYSTEM_IMAGE_DESCRIPTION_BINDING);

    register(RAM_STORAGE_KEY, myRamStorage, myRamStorage.getBinding());
    setControlDescription(myRamStorage, myAvdConfigurationOptionHelpPanel.getDescription(RAM_STORAGE_KEY));

    register(VM_HEAP_STORAGE_KEY, myVmHeapStorage, myVmHeapStorage.getBinding());
    setControlDescription(myVmHeapStorage, myAvdConfigurationOptionHelpPanel.getDescription(VM_HEAP_STORAGE_KEY));

    register(INTERNAL_STORAGE_KEY, myInternalStorage, myInternalStorage.getBinding());
    setControlDescription(myInternalStorage, myAvdConfigurationOptionHelpPanel.getDescription(INTERNAL_STORAGE_KEY));

    register(SD_CARD_STORAGE_KEY, myNewSdCardStorage, myNewSdCardStorage.getBinding());
    setControlDescription(myNewSdCardStorage, myAvdConfigurationOptionHelpPanel.getDescription(SD_CARD_STORAGE_KEY));

    register(USE_SNAPSHOT_KEY, myStoreASnapshotForCheckBox);
    setControlDescription(myStoreASnapshotForCheckBox, myAvdConfigurationOptionHelpPanel.getDescription(USE_SNAPSHOT_KEY));

    register(USE_HOST_GPU_KEY, myUseHostGPUCheckBox);
    setControlDescription(myUseHostGPUCheckBox, myAvdConfigurationOptionHelpPanel.getDescription(USE_HOST_GPU_KEY));

    if (Boolean.FALSE.equals(myState.get(IS_IN_EDIT_MODE_KEY))) {
      registerValueDeriver(RAM_STORAGE_KEY, new MemoryValueDeriver() {
        @Nullable
        @Override
        protected Storage getStorage(@NotNull Device device) {
          return device.getDefaultHardware().getRam();
        }
      });

      registerValueDeriver(VM_HEAP_STORAGE_KEY, new MemoryValueDeriver() {
        @Nullable
        @Override
        protected Storage getStorage(@NotNull Device device) {
          return calculateVmHeap(device);
        }
      });
    }

    registerValueDeriver(DEFAULT_ORIENTATION_KEY, new ValueDeriver<ScreenOrientation>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(DEVICE_DEFINITION_KEY);
      }
      @Nullable
      @Override
      public ScreenOrientation deriveValue(@NotNull ScopedStateStore state,
                                           @Nullable Key changedKey,
                                           @Nullable ScreenOrientation currentValue) {
        Device device = state.get(DEVICE_DEFINITION_KEY);
        if (device != null) {
          return device.getDefaultState().getOrientation();
        } else {
          return null;
        }
      }
    });

    register(DEFAULT_ORIENTATION_KEY, myOrientationToggle, ORIENTATION_BINDING);
    setControlDescription(myOrientationToggle, myAvdConfigurationOptionHelpPanel.getDescription(DEFAULT_ORIENTATION_KEY));
    myOrientationToggle.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myOrientationToggle);
      }
    });

    FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false){
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, true);
      }
    };
    fileChooserDescriptor.setHideIgnored(false);
    myExistingSdCard.addBrowseFolderListener("Select SD Card", "Select an existing SD card image", getProject(), fileChooserDescriptor);
    register(EXISTING_SD_LOCATION, myExistingSdCard);
    setControlDescription(myExistingSdCard, myAvdConfigurationOptionHelpPanel.getDescription(EXISTING_SD_LOCATION));

    register(FRONT_CAMERA_KEY, myFrontCameraCombo, STRING_COMBO_BINDING);
    setControlDescription(myFrontCameraCombo, myAvdConfigurationOptionHelpPanel.getDescription(FRONT_CAMERA_KEY));

    register(BACK_CAMERA_KEY, myBackCameraCombo, STRING_COMBO_BINDING);
    setControlDescription(myBackCameraCombo, myAvdConfigurationOptionHelpPanel.getDescription(BACK_CAMERA_KEY));

    register(SCALE_SELECTION_KEY, myScalingComboBox, new ComponentBinding<AvdScaleFactor, JComboBox>() {
      @Override
      public void addActionListener(@NotNull ActionListener listener, @NotNull JComboBox component) {
        component.addActionListener(listener);
      }

      @Nullable
      @Override
      public AvdScaleFactor getValue(@NotNull JComboBox component) {
        return ((AvdScaleFactor)component.getSelectedItem());
      }

      @Override
      public void setValue(@Nullable AvdScaleFactor newValue, @NotNull JComboBox component) {
        if (newValue != null) {
          component.setSelectedItem(newValue);
        }
      }
    });
    setControlDescription(myScalingComboBox, myAvdConfigurationOptionHelpPanel.getDescription(SCALE_SELECTION_KEY));

    register(NETWORK_LATENCY_KEY, myLatencyCombo, STRING_COMBO_BINDING);
    setControlDescription(myLatencyCombo, myAvdConfigurationOptionHelpPanel.getDescription(NETWORK_LATENCY_KEY));

    register(NETWORK_SPEED_KEY, mySpeedCombo, STRING_COMBO_BINDING);
    setControlDescription(mySpeedCombo, myAvdConfigurationOptionHelpPanel.getDescription(NETWORK_SPEED_KEY));

    register(KEY_DESCRIPTION, myAvdConfigurationOptionHelpPanel, new ComponentBinding<String, AvdConfigurationOptionHelpPanel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull AvdConfigurationOptionHelpPanel component) {
        component.setDescriptionText(newValue);
      }
    });

    File currentSkinFile = myState.get(CUSTOM_SKIN_FILE_KEY);
    if (currentSkinFile != null) {
      myState.put(CUSTOM_SKIN_PATH_KEY, currentSkinFile.getPath());
    }
    register(CUSTOM_SKIN_PATH_KEY, myCustomSkinPath);
    FileChooserDescriptor skinChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    myCustomSkinPath.addBrowseFolderListener("Select Custom Skin", "Select the directory containing your custom skin definition",
                                                 getProject(), skinChooserDescriptor);
    setControlDescription(myCustomSkinPath, myAvdConfigurationOptionHelpPanel.getDescription(CUSTOM_SKIN_FILE_KEY));

    registerValueDeriver(CUSTOM_SKIN_FILE_KEY, new ValueDeriver<File>() {
      @Nullable
      @Override
      public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
        return makeSetOf(CUSTOM_SKIN_PATH_KEY);
      }

      @Nullable
      @Override
      public File deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable File currentValue) {
        String path = state.get(CUSTOM_SKIN_PATH_KEY);
        if (path != null) {
          File file = new File(path);
          if (file.isDirectory()) {
            return file;
          }
        }
        return null;
      }
    });

    invokeUpdate(null);
  }

  private void createUIComponents() {
    myOrientationToggle = new ASGallery<ScreenOrientation>(JBList.createDefaultListModel(ScreenOrientation.PORTRAIT,
                                                                                         ScreenOrientation.LANDSCAPE),
                                                           new Function<ScreenOrientation, Image>() {
                                                             @Override
                                                             public Image apply(ScreenOrientation input) {
                                                               return ChooseModuleTypeStep.iconToImage(ORIENTATIONS.get(input).myIcon);
                                                             }
                                                           },
                                                           new Function<ScreenOrientation, String>() {
                                                             @Override
                                                             public String apply(ScreenOrientation input) {
                                                               return ORIENTATIONS.get(input).myName;
                                                             }
                                                           }, new Dimension(50,50));
    myOrientationToggle.setCellMargin(new Insets(3, 5, 3, 5));
    myOrientationToggle.setBackground(JBColor.background());
    myOrientationToggle.setForeground(JBColor.foreground());
    myScalingComboBox = new ComboBox(new EnumComboBoxModel<AvdScaleFactor>(AvdScaleFactor.class));
    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget("");
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Configure AVD Options";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private static final class NamedIcon {

    @NotNull private final String myName;

    @NotNull private final Icon myIcon;
    public NamedIcon(@NotNull String name, @NotNull Icon icon) {
      myName = name;
      myIcon = icon;
    }
  }

  private static final Map<ScreenOrientation, NamedIcon> ORIENTATIONS = ImmutableMap.of(ScreenOrientation.PORTRAIT,
                                                                                        new NamedIcon("Portrait", AndroidIcons.Portrait),
                                                                                        ScreenOrientation.LANDSCAPE,
                                                                                        new NamedIcon("Landscape", AndroidIcons.Landscape));

  private static final ComponentBinding<Device, JBLabel> DEVICE_NAME_BINDING = new ComponentBinding<Device, JBLabel>() {
    @Override
    public void setValue(@Nullable Device newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        component.setText(newValue.getDisplayName());
        Icon icon = DeviceDefinitionPreview.getIcon(newValue);

        component.setIcon(icon);
      }
    }
  };

  private static final ComponentBinding<Device, JBLabel> DEVICE_DETAILS_BINDING = new ComponentBinding<Device, JBLabel>() {
    @Override
    public void setValue(@Nullable Device newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        String description = Joiner.on(' ')
          .join(DeviceDefinitionList.getDiagonalSize(newValue), DeviceDefinitionList.getDimensionString(newValue),
                DeviceDefinitionList.getDensityString(newValue));
        component.setText(description);
      }
    }
  };

  private static final ComponentBinding<SystemImageDescription, JBLabel> SYSTEM_IMAGE_NAME_BINDING = new ComponentBinding<SystemImageDescription, JBLabel>() {
    @Override
    public void setValue(@Nullable SystemImageDescription newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        String codeName = SystemImagePreview.getCodeName(newValue);
        component.setText(codeName);
        try {
          Icon icon = IconLoader.getIcon(String.format("/icons/versions/%s_32.png", codeName), AndroidIcons.class);
          component.setIcon(icon);
        } catch (RuntimeException e) {
          // Pass
        }
      }
    }
  };

  private static final ComponentBinding<SystemImageDescription, JBLabel> SYSTEM_IMAGE_DESCRIPTION_BINDING = new ComponentBinding<SystemImageDescription, JBLabel>() {
    @Override
    public void setValue(@Nullable SystemImageDescription newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        component.setText(newValue.target.getFullName() + " " + newValue.systemImage.getAbiType());
      }
    }
  };

  public static final ComponentBinding<ScreenOrientation, ASGallery<ScreenOrientation>> ORIENTATION_BINDING =
    new ComponentBinding<ScreenOrientation, ASGallery<ScreenOrientation>>() {
      @Nullable
      @Override
      public ScreenOrientation getValue(@NotNull ASGallery<ScreenOrientation> component) {
        return component.getSelectedElement();
      }

      @Override
      public void setValue(@Nullable ScreenOrientation newValue, @NotNull ASGallery<ScreenOrientation> component) {
        component.setSelectedElement(newValue);
      }
    };

  private static final ComponentBinding<String, JComboBox> STRING_COMBO_BINDING = new ComponentBinding<String, JComboBox>() {
    @Override
    public void setValue(@Nullable String newValue, @NotNull JComboBox component) {
      if (newValue == null) {
        return;
      }
      for (int i = 0; i < component.getItemCount(); i++) {
        if (newValue.equalsIgnoreCase((String)component.getItemAt(i))) {
          component.setSelectedIndex(i);
        }
      }
    }

    @Nullable
    @Override
    public String getValue(@NotNull JComboBox component) {
      return component.getSelectedItem().toString().toLowerCase();
    }

    @Override
    public void addItemListener(@NotNull ItemListener listener, @NotNull JComboBox component) {
      component.addItemListener(listener);
    }
  };

  private void registerAdvancedOptionsVisibility() {
    myAdvancedOptionsComponents = ImmutableSet.<JComponent>of(myMemoryAndStoragePanel, myCameraPanel, myNetworkPanel,
                                                              mySkinPanel);
  }

  /**
   * Show or hide the "advanced" control panels.
   */
  private void toggleAdvancedSettings(boolean show) {
    for (JComponent c : myAdvancedOptionsComponents) {
      c.setVisible(show);
    }
    myRoot.validate();
  }

  /**
   * Enable/Disable controls based on the capabilities of the selected device. For example, some devices may
   * not have a front facing camera.
   */
  private void toggleOptionals(@NotNull Device device) {
    myFrontCameraCombo.setEnabled(device.getDefaultHardware().getCamera(CameraLocation.FRONT) != null);
    myBackCameraCombo.setEnabled(device.getDefaultHardware().getCamera(CameraLocation.BACK) != null);
    myOrientationToggle.setEnabled(device.getDefaultState().getOrientation() != ScreenOrientation.SQUARE);
  }

  private static Storage calculateVmHeap(@NotNull Device device) {
    // Set the default VM heap size. This is based on the Android CDD minimums for each
    // screen size and density.
    Screen s = device.getDefaultHardware().getScreen();
    ScreenSize size = s.getSize();
    Density density = s.getPixelDensity();
    int vmHeapSize = 32;
    if (size.equals(ScreenSize.XLARGE)) {
      switch (density) {
        case LOW:
        case MEDIUM:
          vmHeapSize = 32;
          break;
        case TV:
        case HIGH:
          vmHeapSize = 64;
          break;
        case XHIGH:
        case XXHIGH:
        case XXXHIGH:
          vmHeapSize = 128;
          break;
        case NODPI:
          break;
      }
    } else {
      switch (density) {
        case LOW:
        case MEDIUM:
          vmHeapSize = 16;
          break;
        case TV:
        case HIGH:
          vmHeapSize = 32;
          break;
        case XHIGH:
        case XXHIGH:
        case XXXHIGH:
          vmHeapSize = 64;
          break;
        case NODPI:
          break;
      }
    }
    return new Storage(vmHeapSize, Unit.MiB);
  }

  private abstract static class MemoryValueDeriver extends ValueDeriver<Storage> {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(DEVICE_DEFINITION_KEY);
    }

    @Nullable
    @Override
    public Storage deriveValue(@NotNull ScopedStateStore state,
                               @Nullable Key changedKey,
                               @Nullable Storage currentValue) {
      Device device = state.get(DEVICE_DEFINITION_KEY);
      if (device != null) {
        return getStorage(device);
      } else {
        return null;
      }
    }

    @Nullable
    protected abstract Storage getStorage(@NotNull Device device);
  }
}
