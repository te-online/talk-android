/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code:
 *
 *
 * Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */

package com.nextcloud.talk.webrtc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.utils.power.PowerManagerUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.greenrobot.eventbus.EventBus;
import org.webrtc.ThreadUtils;

/**
 * MagicAudioManager manages all audio related parts of the AppRTC demo.
 */
public class MagicAudioManager {
  private static final String TAG = "MagicAudioManager";
  private static final String SPEAKERPHONE_AUTO = "auto";
  private static final String SPEAKERPHONE_FALSE = "false";
  private final Context magicContext;
  // Handles all tasks related to Bluetooth headset devices.
  private final MagicBluetoothManager bluetoothManager;
  // Contains speakerphone setting: auto, true or false
  private String useSpeakerphone;
  private AudioManager audioManager;
  private AudioManagerEvents audioManagerEvents;
  private AudioManagerState amState;
  private int savedAudioMode = AudioManager.MODE_INVALID;
  private boolean savedIsSpeakerPhoneOn = false;
  private boolean savedIsMicrophoneMute = false;
  private boolean hasWiredHeadset = false;
  // Default audio device; speaker phone for video calls or earpiece for audio
  // only calls.
  private AudioDevice defaultAudioDevice;
  // Contains the currently selected audio device.
  // This device is changed automatically using a certain scheme where e.g.
  // a wired headset "wins" over speaker phone. It is also possible for a
  // user to explicitly select a device (and overrid any predefined scheme).
  // See |userSelectedAudioDevice| for details.
  private AudioDevice selectedAudioDevice;
  // Contains the user-selected audio device which overrides the predefined
  // selection scheme.
  // TODO(henrika): always set to AudioDevice.NONE today. Add support for
  // explicit selection based on choice by userSelectedAudioDevice.
  private AudioDevice userSelectedAudioDevice;
  // Proximity sensor object. It measures the proximity of an object in cm
  // relative to the view screen of a device and can therefore be used to
  // assist device switching (close to ear <=> use headset earpiece if
  // available, far from ear <=> use speaker phone).
  private MagicProximitySensor proximitySensor = null;
  // Contains a list of available audio devices. A Set collection is used to
  // avoid duplicate elements.
  private Set<AudioDevice> audioDevices = new HashSet<>();
  // Broadcast receiver for wired headset intent broadcasts.
  private BroadcastReceiver wiredHeadsetReceiver;
  // Callback method for changes in audio focus.
  private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

  private PowerManagerUtils powerManagerUtils;

  private MagicAudioManager(Context context, boolean useProximitySensor) {
    Log.d(TAG, "ctor");
    ThreadUtils.checkIsOnMainThread();
    magicContext = context;
    audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    bluetoothManager = MagicBluetoothManager.create(context, this);
    wiredHeadsetReceiver = new WiredHeadsetReceiver();
    amState = AudioManagerState.UNINITIALIZED;

    powerManagerUtils = new PowerManagerUtils();
    powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);

    if (useProximitySensor) {
      useSpeakerphone = SPEAKERPHONE_AUTO;
    } else {
      useSpeakerphone = SPEAKERPHONE_FALSE;
    }

    if (useSpeakerphone.equals(SPEAKERPHONE_FALSE)) {
      defaultAudioDevice = AudioDevice.EARPIECE;
    } else {
      defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    // Create and initialize the proximity sensor.
    // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
    // Note that, the sensor will not be active until start() has been called.
    proximitySensor = MagicProximitySensor.create(context, new Runnable() {
      // This method will be called each time a viewState change is detected.
      // Example: user holds his hand over the device (closer than ~5 cm),
      // or removes his hand from the device.
      public void run() {
        onProximitySensorChangedState();
      }
    });

    Log.d(TAG, "defaultAudioDevice: " + defaultAudioDevice);
  }

  /**
   * Construction.
   */
  public static MagicAudioManager create(Context context, boolean useProximitySensor) {
    return new MagicAudioManager(context, useProximitySensor);
  }

  public void toggleUseSpeakerphone() {
    if (useSpeakerphone.equals(SPEAKERPHONE_FALSE)) {
      useSpeakerphone = SPEAKERPHONE_AUTO;
      setDefaultAudioDevice(AudioDevice.SPEAKER_PHONE);
    } else {
      useSpeakerphone = SPEAKERPHONE_FALSE;
      setDefaultAudioDevice(AudioDevice.EARPIECE);
    }

    updateAudioDeviceState();
  }

  public boolean isSpeakerphoneAutoOn() {
    return (useSpeakerphone.equals(SPEAKERPHONE_AUTO));
  }

  /**
   * This method is called when the proximity sensor reports a viewState change,
   * e.g. from "NEAR to FAR" or from "FAR to NEAR".
   */
  private void onProximitySensorChangedState() {

    if (!useSpeakerphone.equals(SPEAKERPHONE_AUTO)) {
      return;
    }

    // The proximity sensor should only be activated when there are exactly two
    // available audio devices.
    if (audioDevices.size() == 2 && audioDevices.contains(MagicAudioManager.AudioDevice.EARPIECE)
        && audioDevices.contains(MagicAudioManager.AudioDevice.SPEAKER_PHONE)) {
      if (proximitySensor.sensorReportsNearState()) {
        // Sensor reports that a "handset is being held up to a person's ear",
        // or "something is covering the light sensor".
        setAudioDeviceInternal(MagicAudioManager.AudioDevice.EARPIECE);

        EventBus.getDefault()
            .post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                .SENSOR_NEAR, null, null, null, null));
      } else {
        // Sensor reports that a "handset is removed from a person's ear", or
        // "the light sensor is no longer covered".
        setAudioDeviceInternal(MagicAudioManager.AudioDevice.SPEAKER_PHONE);

        EventBus.getDefault()
            .post(new PeerConnectionEvent(PeerConnectionEvent.PeerConnectionEventType
                .SENSOR_FAR, null, null, null, null));
      }
    }
  }

  public void start(AudioManagerEvents audioManagerEvents) {
    Log.d(TAG, "start");
    ThreadUtils.checkIsOnMainThread();
    if (amState == AudioManagerState.RUNNING) {
      Log.e(TAG, "AudioManager is already active");
      return;
    }
    // TODO(henrika): perhaps call new method called preInitAudio() here if UNINITIALIZED.

    Log.d(TAG, "AudioManager starts...");
    this.audioManagerEvents = audioManagerEvents;
    amState = AudioManagerState.RUNNING;

    // Store current audio viewState so we can restore it when stop() is called.
    savedAudioMode = audioManager.getMode();
    savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
    savedIsMicrophoneMute = audioManager.isMicrophoneMute();
    hasWiredHeadset = hasWiredHeadset();

    // Create an AudioManager.OnAudioFocusChangeListener instance.
    audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
      // Called on the listener to notify if the audio focus for this listener has been changed.
      // The |focusChange| value indicates whether the focus was gained, whether the focus was lost,
      // and whether that loss is transient, or whether the new focus holder will hold it for an
      // unknown amount of time.
      // TODO(henrika): possibly extend support of handling audio-focus changes. Only contains
      // logging for now.
      @Override
      public void onAudioFocusChange(int focusChange) {
        String typeOfChange = "AUDIOFOCUS_NOT_DEFINED";
        switch (focusChange) {
          case AudioManager.AUDIOFOCUS_GAIN:
            typeOfChange = "AUDIOFOCUS_GAIN";
            break;
          case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
            break;
          case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
            typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
            break;
          case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
            typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
            break;
          case AudioManager.AUDIOFOCUS_LOSS:
            typeOfChange = "AUDIOFOCUS_LOSS";
            break;
          case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
            break;
          case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
            break;
          default:
            typeOfChange = "AUDIOFOCUS_INVALID";
            break;
        }
        Log.d(TAG, "onAudioFocusChange: " + typeOfChange);
      }
    };

    // Request audio playout focus (without ducking) and install listener for changes in focus.
    int result = audioManager.requestAudioFocus(audioFocusChangeListener,
        AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      Log.d(TAG, "Audio focus request granted for VOICE_CALL streams");
    } else {
      Log.e(TAG, "Audio focus request failed");
    }

    // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
    // required to be in this mode when playout and/or recording starts for
    // best possible VoIP performance.
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    // Always disable microphone mute during a WebRTC call.
    setMicrophoneMute(false);

    // Set initial device states.
    userSelectedAudioDevice = AudioDevice.NONE;
    selectedAudioDevice = AudioDevice.NONE;
    audioDevices.clear();

    // Initialize and start Bluetooth if a BT device is available or initiate
    // detection of new (enabled) BT devices.
    bluetoothManager.start();

    // Do initial selection of audio device. This setting can later be changed
    // either by adding/removing a BT or wired headset or by covering/uncovering
    // the proximity sensor.
    updateAudioDeviceState();

    proximitySensor.start();
    // Register receiver for broadcast intents related to adding/removing a
    // wired headset.
    registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    Log.d(TAG, "AudioManager started");
  }

  public void stop() {
    Log.d(TAG, "stop");
    ThreadUtils.checkIsOnMainThread();
    if (amState != AudioManagerState.RUNNING) {
      Log.e(TAG, "Trying to stop AudioManager in incorrect viewState: " + amState);
      return;
    }
    amState = AudioManagerState.UNINITIALIZED;

    unregisterReceiver(wiredHeadsetReceiver);

    bluetoothManager.stop();

    // Restore previously stored audio states.
    setSpeakerphoneOn(savedIsSpeakerPhoneOn);
    setMicrophoneMute(savedIsMicrophoneMute);
    audioManager.setMode(savedAudioMode);

    // Abandon audio focus. Gives the previous focus owner, if any, focus.
    audioManager.abandonAudioFocus(audioFocusChangeListener);
    audioFocusChangeListener = null;
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams");

    if (proximitySensor != null) {
      proximitySensor.stop();
      proximitySensor = null;
    }

    powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE);

    audioManagerEvents = null;
    Log.d(TAG, "AudioManager stopped");
  }

  /**
   * Changes selection of the currently active audio device.
   */
  private void setAudioDeviceInternal(AudioDevice device) {
    Log.d(TAG, "setAudioDeviceInternal(device=" + device + ")");

    if (audioDevices.contains(device)) {

      switch (device) {
        case SPEAKER_PHONE:
          setSpeakerphoneOn(true);
          break;
        case EARPIECE:
          setSpeakerphoneOn(false);
          break;
        case WIRED_HEADSET:
          setSpeakerphoneOn(false);
          break;
        case BLUETOOTH:
          setSpeakerphoneOn(false);
          break;
        default:
          Log.e(TAG, "Invalid audio device selection");
          break;
      }
      selectedAudioDevice = device;
    }
  }

  /**
   * Changes default audio device.
   * TODO(henrika): add usage of this method in the AppRTCMobile client.
   */
  public void setDefaultAudioDevice(AudioDevice defaultDevice) {
    ThreadUtils.checkIsOnMainThread();
    switch (defaultDevice) {
      case SPEAKER_PHONE:
        defaultAudioDevice = defaultDevice;
        break;
      case EARPIECE:
        if (hasEarpiece()) {
          defaultAudioDevice = defaultDevice;
        } else {
          defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
        }
        break;
      default:
        Log.e(TAG, "Invalid default audio device selection");
        break;
    }
    Log.d(TAG, "setDefaultAudioDevice(device=" + defaultAudioDevice + ")");
    updateAudioDeviceState();
  }

  /**
   * Changes selection of the currently active audio device.
   */
  public void selectAudioDevice(AudioDevice device) {
    ThreadUtils.checkIsOnMainThread();
    if (!audioDevices.contains(device)) {
      Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
    }
    userSelectedAudioDevice = device;
    updateAudioDeviceState();
  }

  /**
   * Returns current set of available/selectable audio devices.
   */
  public Set<AudioDevice> getAudioDevices() {
    ThreadUtils.checkIsOnMainThread();
    return Collections.unmodifiableSet(new HashSet<AudioDevice>(audioDevices));
  }

  /**
   * Returns the currently selected audio device.
   */
  public AudioDevice getSelectedAudioDevice() {
    ThreadUtils.checkIsOnMainThread();
    return selectedAudioDevice;
  }

  /**
   * Helper method for receiver registration.
   */
  private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    magicContext.registerReceiver(receiver, filter);
  }

  /**
   * Helper method for unregistration of an existing receiver.
   */
  private void unregisterReceiver(BroadcastReceiver receiver) {
    magicContext.unregisterReceiver(receiver);
  }

  /**
   * Sets the speaker phone mode.
   */
  private void setSpeakerphoneOn(boolean on) {
    boolean wasOn = audioManager.isSpeakerphoneOn();
    if (wasOn == on) {
      return;
    }
    audioManager.setSpeakerphoneOn(on);
  }

  /**
   * Sets the microphone mute viewState.
   */
  private void setMicrophoneMute(boolean on) {
    boolean wasMuted = audioManager.isMicrophoneMute();
    if (wasMuted == on) {
      return;
    }
    audioManager.setMicrophoneMute(on);
  }

  /**
   * Gets the current earpiece viewState.
   */
  private boolean hasEarpiece() {
    return magicContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
  }

  /**
   * Checks whether a wired headset is connected or not.
   * This is not a valid indication that audio playback is actually over
   * the wired headset as audio routing depends on other conditions. We
   * only use it as an early indicator (during initialization) of an attached
   * wired headset.
   */
  @Deprecated
  private boolean hasWiredHeadset() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return audioManager.isWiredHeadsetOn();
    } else {
      final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
      for (AudioDeviceInfo device : devices) {
        final int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          Log.d(TAG, "hasWiredHeadset: found wired headset");
          return true;
        } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
          Log.d(TAG, "hasWiredHeadset: found USB audio device");
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Updates list of possible audio devices and make new device selection.
   * TODO(henrika): add unit test to verify all viewState transitions.
   */
  public void updateAudioDeviceState() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "--- updateAudioDeviceState: "
        + "wired headset=" + hasWiredHeadset + ", "
        + "BT viewState=" + bluetoothManager.getState());
    Log.d(TAG, "Device status: "
        + "available=" + audioDevices + ", "
        + "selected=" + selectedAudioDevice + ", "
        + "user selected=" + userSelectedAudioDevice);

    // Check if any Bluetooth headset is connected. The BT viewState will
    // change accordingly.
    // TODO(henrika): perhaps wrap required viewState into BT manager.
    if (bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_UNAVAILABLE
        || bluetoothManager.getState() == MagicBluetoothManager.State.SCO_DISCONNECTING) {
      bluetoothManager.updateDevice();
    }

    // Update the set of available audio devices.
    Set<AudioDevice> newAudioDevices = new HashSet<>();

    if (bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTED
        || bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_AVAILABLE) {
      newAudioDevices.add(AudioDevice.BLUETOOTH);
    }

    if (hasWiredHeadset) {
      // If a wired headset is connected, then it is the only possible option.
      newAudioDevices.add(AudioDevice.WIRED_HEADSET);
    } else {
      // No wired headset, hence the audio-device list can contain speaker
      // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
      newAudioDevices.add(AudioDevice.SPEAKER_PHONE);
      if (hasEarpiece()) {
        newAudioDevices.add(AudioDevice.EARPIECE);
      }
    }
    // Store viewState which is set to true if the device list has changed.
    boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
    // Update the existing audio device set.
    audioDevices = newAudioDevices;
    // Correct user selected audio devices if needed.
    if (bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_UNAVAILABLE
        && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
      // If BT is not available, it can't be the user selection.
      userSelectedAudioDevice = AudioDevice.NONE;
    }
    if (hasWiredHeadset && userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
      // If user selected speaker phone, but then plugged wired headset then make
      // wired headset as user selected device.
      userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
    }
    if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
      // If user selected wired headset, but then unplugged wired headset then make
      // speaker phone as user selected device.
      userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    // Need to start Bluetooth if it is available and user either selected it explicitly or
    // user did not select any output device.
    boolean needBluetoothAudioStart =
        bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_AVAILABLE
            && (userSelectedAudioDevice == AudioDevice.NONE
            || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

    // Need to stop Bluetooth audio if user selected different device and
    // Bluetooth SCO connection is established or in the process.
    boolean needBluetoothAudioStop =
        (bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTING)
            && (userSelectedAudioDevice != AudioDevice.NONE
            && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

    if (bluetoothManager.getState() == MagicBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTED) {
      Log.d(TAG, "Need BT audio: start=" + needBluetoothAudioStart + ", "
          + "stop=" + needBluetoothAudioStop + ", "
          + "BT viewState=" + bluetoothManager.getState());
    }

    // Start or stop Bluetooth SCO connection given states set earlier.
    if (needBluetoothAudioStop) {
      bluetoothManager.stopScoAudio();
      bluetoothManager.updateDevice();
    }

    // Attempt to start Bluetooth SCO audio (takes a few second to start).
    if (needBluetoothAudioStart &&
        !needBluetoothAudioStop &&
        !bluetoothManager.startScoAudio()) {
      // Remove BLUETOOTH from list of available devices since SCO failed.
      audioDevices.remove(AudioDevice.BLUETOOTH);
      audioDeviceSetUpdated = true;
    }

    // Update selected audio device.
    AudioDevice newAudioDevice = selectedAudioDevice;

    if (bluetoothManager.getState() == MagicBluetoothManager.State.SCO_CONNECTED) {
      // If a Bluetooth is connected, then it should be used as output audio
      // device. Note that it is not sufficient that a headset is available;
      // an active SCO channel must also be up and running.
      newAudioDevice = AudioDevice.BLUETOOTH;
    } else if (hasWiredHeadset) {
      // If a wired headset is connected, but Bluetooth is not, then wired headset is used as
      // audio device.
      newAudioDevice = AudioDevice.WIRED_HEADSET;
    } else {
      // No wired headset and no Bluetooth, hence the audio-device list can contain speaker
      // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
      // |defaultAudioDevice| contains either AudioDevice.SPEAKER_PHONE or AudioDevice.EARPIECE
      // depending on the user's selection.
      newAudioDevice = defaultAudioDevice;
    }
    // Switch to new device but only if there has been any changes.
    if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
      // Do the required device switch.
      setAudioDeviceInternal(newAudioDevice);
      Log.d(TAG, "New device status: "
          + "available=" + audioDevices + ", "
          + "selected=" + newAudioDevice);
      if (audioManagerEvents != null) {
        // Notify a listening client that audio device has been changed.
        audioManagerEvents.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
      }
    }
    Log.d(TAG, "--- updateAudioDeviceState done");
  }

  /**
   * AudioDevice is the names of possible audio devices that we currently
   * support.
   */
  public enum AudioDevice {
    SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE
  }

  /**
   * AudioManager viewState.
   */
  public enum AudioManagerState {
    UNINITIALIZED,
    PREINITIALIZED,
    RUNNING,
  }

  /**
   * Selected audio device change event.
   */
  public interface AudioManagerEvents {
    // Callback fired once audio device is changed or list of available audio devices changed.
    void onAudioDeviceChanged(
        AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
  }

  /* Receiver which handles changes in wired headset availability. */
  private class WiredHeadsetReceiver extends BroadcastReceiver {
    private static final int STATE_UNPLUGGED = 0;
    private static final int STATE_PLUGGED = 1;
    private static final int HAS_NO_MIC = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("viewState", STATE_UNPLUGGED);
      // int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
      // String name = intent.getStringExtra("name");
      hasWiredHeadset = (state == STATE_PLUGGED);
      updateAudioDeviceState();
    }
  }
}
