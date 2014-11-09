package mswat.core.ioManager;

import java.util.ArrayList;
import android.os.Looper;
import android.util.Log;
import mswat.core.CoreController;
import mswat.core.activityManager.HierarchicalService;
import mswat.core.ioManager.Events.InputDevice;


public class Monitor {

	// debugging tag
	private final String LT = "Monitor";

	// List of internal devices (touch, accelerometer)
	ArrayList<InputDevice> dev;

	// Core controller
	CoreController cc;

	private int touchIndex = -1;
	boolean monitoring[];
	private boolean virtualDriveEnable = false;



	/**
	 * Initialises list of devices Initialises Logger if logging is true
	 * 
	 * @param touchIndex2
	 * 
	 * @param logging
	 */
	public Monitor(HierarchicalService hs, int touchIndex) {
		Events ev = new Events();
		dev = ev.Init();

		this.touchIndex = touchIndex;
		monitoring = new boolean[dev.size()];


	}

	/**
	 * Log keystroke if logger is enable TODO
	 * 
	 * @param keypressed
	 */
	public void registerKeystroke(String keypressed) {

		// TODO Log keystrokes
		CoreController.updateLoggers(keypressed);
		// if (logging)
		// Logger.registerTouch("Previous Keystroke: " + keypressed);

	}

	/**
	 * Blocks or unblock the device in index position
	 * 
	 * @param b
	 *            - true to block, false to unblock
	 * @param index
	 *            - device index
	 */
	public void setBlock(int index, boolean b) {
		dev.get(index).takeOver(b);
	}

	/**
	 * Inject event into the device in the position index
	 * 
	 * @param index
	 *            - device index
	 * @param type
	 * @param code
	 * @param value
	 */
	public void inject(int index, int type, int code, int value) {
		dev.get(index).send(index, type, code, value);
	}

	/**
	 * Inject events into the virtual touch device
	 * 
	 * @requires createVirtualTouchDrive() before
	 * @param type
	 * @param code
	 * @param value
	 */
	public void injectToVirtual(int type, int code, int value) {
		if (virtualDriveEnable) {
			Events.sendVirtual(type, code, value);
		}
	}

	/**
	 * Inject events into the touch device
	 * 
	 * @requires createVirtualTouchDrive() before
	 * @param type
	 * @param code
	 * @param value
	 */
	public void injectToTouch(int type, int code, int value) {
		// Log.d(LT, "touch index:" + touchIndex);
		dev.get(touchIndex).send(touchIndex, type, code, value);
	}


	/**
	 * Starts or stops monitoring the device in position index
	 * 
	 * @param index
	 *            - device index
	 * @param b
	 *            - true to monitor, false to stop monitoring
	 */
	public void monitorDevice(final int index, final boolean state) {

		if (state != monitoring[index]) {
			monitoring[index] = state;
			if (monitoring[index]) {

				Thread b = new Thread(new Runnable() {

					public void run() {

						Looper.prepare();
						InputDevice idev = dev.get(index);


						while (monitoring[index]) {

							if (idev.getOpen() && idev.getPollingEvent() == 0) {
								int type = idev.getSuccessfulPollingType();
								int code = idev.getSuccessfulPollingCode();
								int value = idev.getSuccessfulPollingValue();
								int timestamp = idev.getTimeStamp();
//								 Log.d(LT, type + " " + code + " " + value +
//								 " "
//								 + timestamp);
								CoreController.updateIOReceivers(index, type,
										code, value, timestamp);

								

							}
						}
					}
				});
				b.start();
			}
		}

	}

	/**
	 * Returns a String array of the internal devices
	 * 
	 * @return String [] - internal devices names
	 */
	public String[] getDevices() {
		String[] s = new String[dev.size()];
		for (int i = 0; i < dev.size(); i++) {

			s[i] = dev.get(i).getName();
//			Log.d(LT, "Devices: " + s[i]);
		}
		return s;
	}

	/**
	 * Stop all monitoring
	 */
	public void stop() {
		for (int i = 0; i < dev.size(); i++) {
			setBlock(i, false);
			monitoring[i] = false;
		}

	}

	/**
	 * Setup index for touchscreen device
	 */
	public void setupTouch(int index) {
		touchIndex = index;
	}

	/**
	 * Creates a virtual touch drive
	 */
	public void createVirtualTouchDrive(int protocol) {
		Log.d(LT,
				"Virtual drive created "
						+ dev.get(0).createVirtualDrive(
								dev.get(touchIndex).getName(), protocol,
								(int) CoreController.M_WIDTH,
								(int) CoreController.M_HEIGHT));

		virtualDriveEnable = true;
	}

	/**
	 * Starts monitoring the touch device
	 * 
	 * @return index of the touch device if successful -1 if not
	 */
	public int monitorTouch() {

		if (touchIndex != -1) {
			monitorDevice(touchIndex, true);
			return touchIndex;
		} else {
			String devices[] = getDevices();
			for (int i = 0; i < devices.length; i++) {
				if (devices[i].contains("touchscreen")){
					monitorDevice(i, true);
					return i;
				}
			}

			return -1;
		}

	}


}
