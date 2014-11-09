package mswat.core.activityManager;

import mswat.core.CoreController;
import mswat.core.activityManager.scrapper.TreeScrapper;
import mswat.core.ioManager.Monitor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;

import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;

import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class HierarchicalService extends AccessibilityService {

	// Parent node to the current screen content
	AccessibilityNodeInfo currentParent = null;

	private final String LT = "Hier";

	// flag to register keystrokes
	private static boolean logAtTouch = true;

	private SharedPreferences sharedPref;
	private ServicePreferences servPref;
	private Monitor monitor;
	
	//content update
	private TreeScrapper ts;

	// notification on
	boolean noteCheck = false;
	boolean contentCheck = false;

	/**
	 * Triggers whenever happens an event (changeWindow, focus, slide) Updates
	 * the current top parent of the screen contents
	 */
	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {

		/*
		 * Log.d(LT, "----------------------------------------------");
		 * Log.d(LT, "event: " + event.toString());
		 */
		//content update
		ts.updateTree(event); 
		
		CoreController.updateEventReceivers(event);
	
		if (CoreController.noteReceiversSize() > 0 && noteCheck)
			if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
				CoreController
						.updateNotificationReceivers("" + event.getText());
				return;
			}

		// register keystrokes
		if (AccessibilityEvent.eventTypeToString(event.getEventType())
				.contains("TEXT")) {
			if (logAtTouch && !event.isPassword()) {
				if (event.getRemovedCount() > event.getAddedCount())
					monitor.registerKeystroke("<<" + "," + event.getEventTime());
				else {

					if (event.getRemovedCount() != event.getAddedCount()) {
						// When the before text is a space it needs this
						// to
						// properly
						// detect the backspace
						// Bug a string a char follow by a space "t "
						// when
						// using
						// backspace it detects "t" instead of backspace
						if ((event.getText().size() - 2) == event
								.getBeforeText().length()
								|| (event.getAddedCount() - event
										.getRemovedCount()) > 1)
							monitor.registerKeystroke("<<" + ","
									+ event.getEventTime());
						else {
							String keypressed = event.getText().toString();
							keypressed = ""
									+ keypressed
											.charAt(keypressed.length() - 2);
							if (keypressed.equals(" "))
								keypressed = " ";
							else
								keypressed = "x";
							monitor.registerKeystroke(keypressed + ","
									+ event.getEventTime());
						}
					}

				}
			}

		}

	}

	@Override
	public void onInterrupt() {
		stopService();
	}

	@Override
	public void onDestroy() {
		stopService();
	}

	public void stopService() {
		monitor.stop();
		this.stopSelf();
	}

	/**
	 * Initialise NodeListController Initialise Monitor Initialise
	 * CoreController Initialise Feedback
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void onServiceConnected() {

		// getServiceInfo().flags =
		// AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
		// getServiceInfo().flags =
		// AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

		Log.d(LT, "CONNECTED");

		
 
		getServiceInfo().flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;

		// shared preferences
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		servPref = new ServicePreferences();

	
		if(sharedPref.getBoolean(servPref.LOG, true)){
		IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Intent intent1 = new Intent();

				if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
					intent1.setAction("mswat_screen_off");

					sendBroadcast(intent1);
				} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
					intent1.setAction("mswat_screen_on");
					sendBroadcast(intent1);
				}
			}
		}, intentFilter);
		}

		

		if ((sharedPref.getInt("s_width", (int) CoreController.S_WIDTH)) != 0) {
			CoreController.S_WIDTH = sharedPref.getInt("s_width",
					(int) CoreController.S_WIDTH);
			CoreController.S_HEIGHT = sharedPref.getInt("s_height",
					(int) CoreController.S_HEIGHT);

		} else {
			CoreController.S_WIDTH = 1024;
			CoreController.S_HEIGHT = 960;
		}

		//initialize content updater
		ts= new TreeScrapper();

		// Start tpr
		String tpr = sharedPref.getString(servPref.TPR, "null");
		Intent intent = new Intent();
		intent.setAction("mswat_tpr");
		intent.putExtra("touchRecog", tpr);
		sendBroadcast(intent);

		// initialise monitor
		monitor = new Monitor(this, -1);

		// initialise coreController
		CoreController cc = new CoreController(monitor, this
				, tpr);

		

	}
	

	private Class<?> loadAccessibilityEventSourceClass(AccessibilityEvent event) {
        Class<?> clazz = null;
        String className = event.getClassName().toString();
        try {
            // try the current ClassLoader first
            clazz = getClassLoader().loadClass(className);
        } catch (ClassNotFoundException cnfe) {
            // if the current ClassLoader fails try via creating a package
            // context
            String packageName = event.getPackageName().toString();
            try {
                Context context = getApplicationContext().createPackageContext(packageName,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                clazz = context.getClassLoader().loadClass(className);
            } catch (NameNotFoundException nnfe) {
                Log.e(LT, "Error during loading an event source class: "
                        + event.getClassName() + " " + nnfe);
            } catch (ClassNotFoundException cnfe2) {
                Log.e(LT, "Error during loading an event source class: "
                        + event.getClassName() + " " + cnfe);
            }
        }
        return clazz;
    }
	
	/**
	 * 
	 * @return current content parent
	 */
	public AccessibilityNodeInfo getContentParent() {
		return currentParent;
	}

	// go to home screen
	public boolean home() {
		return performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

	}

	// go to home screen
	public boolean back() {
		return performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
	}

	/**
	 * Register screen size to shared preferences
	 * 
	 * @param width
	 * @param height
	 */
	public void storeScreenSize(int width, int height) {
		sharedPref.edit().putInt("s_width", width).commit();
		sharedPref.edit().putInt("s_height", height).commit();

	}

	/**
	 * Register the touch device index to the shared preferences
	 * 
	 * @param index
	 */
	public void storeTouchIndex(int index) {
		sharedPref.edit().putInt(servPref.TOUCH_INDEX, index).commit();
	}

}
