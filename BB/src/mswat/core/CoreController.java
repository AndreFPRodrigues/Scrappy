package mswat.core;

import java.util.ArrayList;
import java.util.Hashtable;
import mswat.core.activityManager.HierarchicalService;
import mswat.core.activityManager.scrapper.Tree;
import mswat.core.ioManager.Monitor;
import mswat.core.logger.AccessibilityScrapping;
import mswat.core.logger.Logger;
import mswat.interfaces.ContentReceiver;
import mswat.interfaces.EventReceiver;
import mswat.interfaces.IOReceiver;
import mswat.interfaces.NotificationReceiver;
import mswat.touch.TouchRecognizer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;

import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class CoreController {

	// debugging tag
	private final static String LT = "CoreController";

	// Modules where to forward messages
	private static Monitor monitor;


	// List of receivers
	private static Hashtable<Integer, IOReceiver> ioReceivers;
	private static ArrayList<NotificationReceiver> notificationReceivers;
	private static ArrayList<EventReceiver> eventReceivers;
	private static ArrayList<ContentReceiver> contentReceivers;


	private static ArrayList<Logger> loggers;

	// active touch recognizer
	private static TouchRecognizer tpr = null;
	// Context
	private static HierarchicalService hs;

	// Navigation Variables
	public final static int NAV_NEXT = 0;
	public final static int NAV_PREV = 1;
	public final static int SELECT_CURRENT = 2;
	public final static int FOCUS_INDEX = 3;
	public final static int HIGHLIGHT_INDEX = 4;

	// IO Variables
	public final static int SET_BLOCK = 0;
	public final static int MONITOR_DEV = 1;
	public final static int CREATE_VIRTUAL_TOUCH = 2;
	public final static int SETUP_TOUCH = 3;
	public final static int SET_TOUCH_RAW = 4;
	public final static int FOWARD_TO_VIRTUAL = 5;

	// Mapped screen resolution
	public static double M_WIDTH;
	public static double M_HEIGHT;

	// Screen resolution
	public static double S_WIDTH = 1024;
	public static double S_HEIGHT = 960;

	/**
	 * Initialise CoreController
	 * 
	 * @param nController
	 * @param monitor
	 * @param hierarchicalService
	 * @param controller
	 * @param waitForCalibration
	 * @param logNav
	 * @param logAtTouch
	 * @param keyboard
	 * @param tpr2
	 */
	public CoreController(Monitor monitor,
			HierarchicalService hierarchicalService, String tprPreference) {
		CoreController.monitor = monitor;
		hs = hierarchicalService;

		// initialise arrayListReceivers
		ioReceivers = new Hashtable<Integer, IOReceiver>();
		notificationReceivers = new ArrayList<NotificationReceiver>();
		eventReceivers = new ArrayList<EventReceiver>();
		loggers = new ArrayList<Logger>();
		contentReceivers = new ArrayList<ContentReceiver>();
	

		startService();

		// get screen resolution
		WindowManager wm = (WindowManager) hierarchicalService
				.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		DisplayMetrics outMetrics = new DisplayMetrics();
		display.getMetrics(outMetrics);
		Point size = new Point();
		display.getSize(size);
		M_WIDTH = size.x;
		M_HEIGHT = size.y;
	}

	/***********************************
	 * IO Commands and messages
	 * 
	 ************************************* */

	/**
	 * Register logger receiver (receives keystrokes info)
	 * 
	 * @param logger
	 * @return
	 */
	public static int registerLogger(Logger logger) {
		if (logger != null)
			loggers.add(logger);
		return loggers.size() - 1;
	}

	/**
	 * Register IO events receiver
	 * 
	 * @param ioReceiver
	 * @return
	 */
	public static int registerIOReceiver(IOReceiver ioReceiver) {
		int size = ioReceivers.size();
		ioReceivers.put(size, ioReceiver);
		return size;
	}

	/**
	 * Unregister IOReceiver
	 */
	public static void unregisterIOReceiver(int key) {
		ioReceivers.remove(key);
	}

	/**
	 * Io event propagated to io receivers
	 * 
	 * @param device
	 * @param type
	 * @param code
	 * @param value
	 * @param timestamp
	 */
	public static void updateIOReceivers(int device, int type, int code,
			int value, int timestamp) {
		int size = ioReceivers.size();
 
		for (int i = 0; i < size; i++) {

			if (ioReceivers.get(i) != null)
				ioReceivers.get(i).onUpdateIO(device, type, code, value,
						timestamp);
		}
	}


	/**
	 * Keystrokes propagated to loggers
	 * 
	 * @param record
	 *            string representing the keystroke
	 */
	public static void updateLoggers(String record) {
		int size = loggers.size();
		for (int i = 0; i < size; i++) {
			loggers.get(i).logKeystroke(record);
		}

	}

	/**
	 * Forwards the message to the appropriate component
	 * 
	 * @param command
	 *            - SET_BLOCK/MONITOR_DEV/CREATE_VIRTUAL_TOUCH/SETUP_TOUCH
	 * @param index
	 *            - device index for SET_BLOCK/MONITOR_DEV/SETUP_TOUCH
	 * @param state
	 *            - state SET_BLOCK/MONITOR_DEV
	 */
	public static void commandIO(final int command, final int index,
			final boolean state) {

		Thread b = new Thread(new Runnable() {
			public void run() {

				// Separates and fowards messages to the apropriate module
				switch (command) {
				case SET_BLOCK:
					monitor.setBlock(index, state);
					break;
				case MONITOR_DEV:
					monitor.monitorDevice(index, state);
					break;
				case CREATE_VIRTUAL_TOUCH:
					monitor.createVirtualTouchDrive(index);
					break;
				case SETUP_TOUCH:
					hs.storeTouchIndex(index);
					monitor.setupTouch(index);
					break;

				}
			}
		});
		b.start();
	}

	/**
	 * Inject event into touch virtual drive
	 * 
	 * @requires virtual touch driver created
	 * @param t
	 *            type
	 * @param c
	 *            code
	 * @param v
	 *            value
	 */
	public static void injectToVirtual(int t, int c, int v) {
		monitor.injectToVirtual(t, c, v);
	}

	public static void injectToTouch(int t, int c, int v) {
		monitor.injectToTouch(t, c, v);
	}

	/**
	 * Inject event into the device on the position index
	 * 
	 * @param index
	 * @param type
	 * @param code
	 * @param value
	 */
	public static void inject(int index, int type, int code, int value) {
		monitor.inject(index, type, code, value);
	}

	public static int monitorTouch() {
		return monitor.monitorTouch();
	}

	/**
	 * Get list of internal devices (touchscree, keypad, etc)
	 * 
	 * @return
	 */
	public static String[] getDevices() {
		return monitor.getDevices();
	}

	/*************************************************
	 * Navigation and content Commands and messages
	 * 
	 ************************************************** 
	 **/
	/**
	 * Register content update receiver
	 * 
	 * @param contentReceiver
	 * @return
	 */
	public static int registerContentReceiver(ContentReceiver contentReceiver) {
		contentReceivers.add(contentReceiver);
		return contentReceivers.size() - 1;
	} 

	public static void unregisterContent(int index) {
		if(index< contentReceiversSize())
			contentReceivers.remove(index);
	}
	
	public static int contentReceiversSize(){
		return contentReceivers.size();
	}
	
	/**
	 * Content update event propagated to content update receivers
	 * @param content
	 */
	public static void updateContentReceivers(Tree content) {
		int size = contentReceivers.size();
		Log.d(LT, "-------------------------\n " + content.toString());

		for (int i = 0; i < size; i++) {

			contentReceivers.get(i).onUpdateContent(content);
		}
	}
	/**
	 * Register events
	 * 
	 * @param eventReceiver
	 * @return
	 */
	public static int registerEventReceiver(EventReceiver eventReceiver) {
		eventReceivers.add(eventReceiver);
		return eventReceivers.size() - 1;
	}

	public static void unregisterEvent(int index) {
		eventReceivers.remove(index);
	}

	public static void updateEventReceivers(AccessibilityEvent event) {
		int size = eventReceivers.size();
		for (int i = 0; i < size; i++) {

			if (checkEvent(eventReceivers.get(i).getType(), event))
				eventReceivers.get(i).onUpdateEvent(event);
		}
	}

	private static boolean checkEvent(int[] type, AccessibilityEvent event) {
		for (int i = 0; i < type.length; i++) {
			if (type[i] == event.getEventType())
				return true;
		}
		return false;
	}

	/*************************************************
	 * Auxiliary functions
	 * 
	 ************************************************** 
	 **/
	/**
	 * Calculate the mapped coordinate of x
	 * 
	 * @param x
	 * @return
	 */
	public static int xToScreenCoord(double x) {
		return (int) (M_WIDTH / S_WIDTH * x);
	}

	/**
	 * Calculate the mapped coordenate of y
	 * 
	 * @param x
	 * @return
	 */
	public static int yToScreenCoord(double y) {
		return (int) (M_HEIGHT / S_HEIGHT * y);
	}

	public static void stopService() {
		// Broadcast event
		Intent intent = new Intent();
		intent.setAction("mswat_stop");
		hs.sendBroadcast(intent);
		hs.stopService();
	}

	public static void stopServiceNoBroadCast() {
		hs.stopService();
	}

	private static void startService() {

		Log.d(LT, "STARTING SERVICE");
		// Broadcast event
		Intent intent = new Intent();
		intent.setAction("mswat_init");
		hs.sendBroadcast(intent);

	}



	public static void setScreenSize(int width, int height) {
		// height = height - 80;
		CoreController.S_HEIGHT = height;
		CoreController.S_WIDTH = width;
		hs.storeScreenSize(width, height);
	}

	/**
	 * Returns to home
	 * 
	 * @return
	 */
	public static boolean home() {
		return hs.home();
	}

	/**
	 * Returns to home
	 */
	public static boolean back() {
		return hs.back();
	}

	/**
	 * Register a notification receiver
	 * 
	 * @param nr
	 * @return
	 */
	public static int registerNotificationReceiver(NotificationReceiver nr) {
		notificationReceivers.add(nr);
		return notificationReceivers.size() - 1;
	}

	public static int noteReceiversSize() {
		return notificationReceivers.size();
	}

	/**
	 * Update all notifications receivers
	 * 
	 * @param note
	 */
	public static void updateNotificationReceivers(String note) {
		int size = notificationReceivers.size();
		if (note.equals("[]")) {
			return;
		}

		note = note.substring(1, note.length() - 1);

		for (int i = 0; i < size; i++) {
			notificationReceivers.get(i).onNotification(note);
		}

	}

	public static void registerActivateTouch(TouchRecognizer tpr2) {
		tpr = tpr2;
	}

	public static TouchRecognizer getActiveTPR() {
		return tpr;
	}

	// Screen reader function convert pixels to milimeter for android nexus s
	public static int convertToMilY(int y) {
		return (124 * y) / 800 * 5;
	}

	// Screen reader function convert pixels to milimeter for android nexus s
	public static int convertToMilX(int x) {
		return (63 * x) / 480 * 5;
	}

	public static double distanceBetween(double x, double y, double x1,
			double y1) {
		return Math.sqrt(Math.pow(y - y1, 2) + Math.pow(x - x1, 2));
	}

	public static void writeToLog(ArrayList<String> toLog, String filepath) {
		if (loggers.size() > 0) {
			loggers.get(0).registerToLog(toLog, filepath, true);
		}

	}

	public static int currentFileId() {
		return PreferenceManager.getDefaultSharedPreferences(hs).getInt(
				"preFileSeq", 0);
	}

	public static int getIdSequence() {
		if (loggers.size() > 0) {
			return loggers.get(0).getIdSequence();
		}
		return -1;
	}
	
	public static String getCurrentActivityName() {
		
		return AccessibilityScrapping.getCurrentActivityName(hs);
	}

}
