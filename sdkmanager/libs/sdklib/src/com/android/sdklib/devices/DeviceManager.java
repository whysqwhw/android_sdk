/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.devices;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.PkgProps;

import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Manager class for interacting with {@link Device}s within the SDK
 */
public class DeviceManager {

    private final static String sDeviceProfilesProp = "DeviceProfiles";
    private final static Pattern sPathPropertyPattern = Pattern.compile("^" + PkgProps.EXTRA_PATH
            + "=" + sDeviceProfilesProp + "$");
    private ISdkLog mLog;
    private List<Device> mVendorDevices;
    private List<Device> mUserDevices;
    private List<Device> mDefaultDevices;

    public DeviceManager(ISdkLog log) {
        mLog = log;
    }

    /**
     * Returns both vendor provided and user created {@link Device}s.
     *
     * @param sdkLocation
     *            Location of the Android SDK
     * @return A list of both vendor and user provided {@link Device}s
     */
    public List<Device> getDevices(String sdkLocation) {
        List<Device> devices = new ArrayList<Device>(getVendorDevices(sdkLocation));
        devices.addAll(getDefaultDevices());
        devices.addAll(getUserDevices());
        return devices;
    }

    public List<Device> getDefaultDevices() {
        synchronized (this) {
            if (mDefaultDevices == null) {
                try {
                    mDefaultDevices = DeviceParser.parse(
                            DeviceManager.class.getResourceAsStream(SdkConstants.FN_DEVICES_XML));
                } catch (IllegalStateException e) {
                    // The device builders can throw IllegalStateExceptions if
                    // build gets called before everything is properly setup
                    mLog.error(e, null);
                    mDefaultDevices = new ArrayList<Device>();
                } catch (Exception e) {
                    mLog.error(null, "Error reading default devices");
                    mDefaultDevices = new ArrayList<Device>();
                }
            }
        }
        return mDefaultDevices;
    }

    /**
     * Returns all vendor provided {@link Device}s
     *
     * @param sdkLocation
     *            Location of the Android SDK
     * @return A list of vendor provided {@link Device}s
     */
    public List<Device> getVendorDevices(String sdkLocation) {
        synchronized (this) {
            if (mVendorDevices == null) {
                List<Device> devices = new ArrayList<Device>();
                File extrasFolder = new File(sdkLocation, SdkConstants.FD_EXTRAS);
                List<File> deviceDirs = getExtraDirs(extrasFolder);
                for (File deviceDir : deviceDirs) {
                    File deviceXml = new File(deviceDir, SdkConstants.FN_DEVICES_XML);
                    if (deviceXml.isFile()) {
                        devices.addAll(loadDevices(deviceXml));
                    }
                }
                mVendorDevices = devices;
            }
        }
        return mVendorDevices;
    }

    /**
     * Returns all user created {@link Device}s
     *
     * @return All user created {@link Device}s
     */
    public List<Device> getUserDevices() {
        synchronized (this) {
            if (mUserDevices == null) {
                // User devices should be saved out to
                // $HOME/.android/devices.xml
                mUserDevices = new ArrayList<Device>();
                try {
                    File userDevicesFile = new File(AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    mUserDevices.addAll(loadDevices(userDevicesFile));
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't load user devices: %1$s", e.getMessage());
                }
            }
        }
        return mUserDevices;
    }

    public void saveUserDevices() {
        synchronized (this) {
            if (mUserDevices != null && mUserDevices.size() != 0) {
                File userDevicesFile;
                try {
                    userDevicesFile = new File(AndroidLocation.getFolder(),
                            SdkConstants.FN_DEVICES_XML);
                    DeviceWriter.writeToXml(new FileOutputStream(userDevicesFile), mUserDevices);
                } catch (AndroidLocationException e) {
                    mLog.warning("Couldn't find user directory: %1$s", e.getMessage());
                } catch (FileNotFoundException e) {
                    mLog.warning("Couldn't open file: %1$s", e.getMessage());
                } catch (ParserConfigurationException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerFactoryConfigurationError e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                } catch (TransformerException e) {
                    mLog.warning("Error writing file: %1$s", e.getMessage());
                }
            }
        }
    }

    /**
     * Returns hardware properties (defined in hardware.ini) as a {@link Map}.
     * @param The {@link State} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(State s) {
        Hardware hw = s.getHardware();
        Map<String, String> props = new HashMap<String, String>();
        props.put("hw.ramSize", Long.toString(hw.getRam().getSizeAsUnit(Unit.MiB)));
        props.put("hw.mainKeys", getBooleanVal(hw.getButtonType().equals(ButtonType.HARD)));
        props.put("hw.trackBall", getBooleanVal(hw.getNav().equals(Navigation.TRACKBALL)));
        props.put("hw.keyboard", getBooleanVal(hw.getKeyboard().equals(Keyboard.QWERTY)));
        props.put("hw.dPad", getBooleanVal(hw.getNav().equals(Navigation.DPAD)));
        Set<Sensor> sensors = hw.getSensors();
        props.put("hw.gps", getBooleanVal(sensors.contains(Sensor.GPS)));
        props.put("hw.battery", getBooleanVal(hw.getChargeType().equals(PowerType.BATTERY)));
        props.put("hw.accelerometer", getBooleanVal(sensors.contains(Sensor.ACCELEROMETER)));
        props.put("hw.audioInput", getBooleanVal(hw.hasMic()));
        props.put("hw.sdCard", getBooleanVal(hw.getRemovableStorage().size() > 0));
        props.put("hw.sdCard", getBooleanVal(hw.getRemovableStorage().size() > 0));
        props.put("hw.lcd.density",
                Integer.toString(hw.getScreen().getPixelDensity().getDpiValue()));
        props.put("hw.sensors.proximity",
                getBooleanVal(sensors.contains(Sensor.PROXIMITY_SENSOR)));
        return props;
    }

    /**
     * Returns the hardware properties defined in {@link AvdManager.HARDWARE_INI} as a {@link Map}.
     * @param The {@link Device} from which to derive the hardware properties.
     * @return A {@link Map} of hardware properties.
     */
    public static Map<String, String> getHardwareProperties(Device d) {
        Map<String, String> props = getHardwareProperties(d.getDefaultState());
        for (State s : d.getAllStates()) {
            if (s.getKeyState().equals(KeyboardState.HIDDEN)){
                props.put("hw.keyboard.lid", getBooleanVal(true));
            }
        }
        return getHardwareProperties(d.getDefaultState());
    }

    private static String getBooleanVal(boolean bool) {
        if (bool) {
            return HardwareProperties.BOOLEAN_VALUES[0];
        }
        return HardwareProperties.BOOLEAN_VALUES[1];
    }

    private Collection<Device> loadDevices(File deviceXml) {
        try {
            return DeviceParser.parse(deviceXml);
        } catch (SAXException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (ParserConfigurationException e) {
            mLog.error(null, "Error parsing %1$s", deviceXml.getAbsolutePath());
        } catch (IOException e) {
            mLog.error(null, "Error reading %1$s", deviceXml.getAbsolutePath());
        } catch (IllegalStateException e) {
            // The device builders can throw IllegalStateExceptions if
            // build gets called before everything is properly setup
            mLog.error(e, null);
        }
        return new ArrayList<Device>();
    }

    /* Returns all of DeviceProfiles in the extras/ folder */
    private List<File> getExtraDirs(File extrasFolder) {
        List<File> extraDirs = new ArrayList<File>();
        // All OEM provided device profiles are in
        // $SDK/extras/$VENDOR/$ITEM/devices.xml
        if (extrasFolder != null && extrasFolder.isDirectory()) {
            for (File vendor : extrasFolder.listFiles()) {
                if (vendor.isDirectory()) {
                    for (File item : vendor.listFiles()) {
                        if (item.isDirectory() && isDevicesExtra(item)) {
                            extraDirs.add(item);
                        }
                    }
                }
            }
        }

        return extraDirs;
    }

    /*
     * Returns whether a specific folder for a specific vendor is a
     * DeviceProfiles folder
     */
    private boolean isDevicesExtra(File item) {
        File properties = new File(item, SdkConstants.FN_SOURCE_PROP);
        try {
            BufferedReader propertiesReader = new BufferedReader(new FileReader(properties));
            try {
                String line;
                while ((line = propertiesReader.readLine()) != null) {
                    Matcher m = sPathPropertyPattern.matcher(line);
                    if (m.matches()) {
                        return true;
                    }
                }
            } finally {
                propertiesReader.close();
            }
        } catch (IOException ignore) { }
        return false;
    }
}
