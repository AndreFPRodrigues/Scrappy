package mswat.core.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import mswat.core.CoreController;
import mswat.interfaces.EventReceiver;
import mswat.interfaces.IOReceiver;
import mswat.touch.TouchEvent;
import mswat.touch.TouchRecognizer;

public class Logger extends BroadcastReceiver implements IOReceiver,
		EventReceiver {

	private final static String LT = "TBB";
	private static Context context;
	private static int touchDevice;
	private int ioID;
	private static int eventRegisterID;
	private TouchRecognizer tpr;
	private int lastRead = -1;
	private static ArrayList<String> treeToLog = new ArrayList<String>();

	private int devSpecialKeys;
	private int devHomeAndVolume;

	// Strings in queue to log
	private static ArrayList<String> ioToLog = new ArrayList<String>();
	// Strings in queue to text Log
	private static ArrayList<String> textLog = new ArrayList<String>();

	private int id = 0;

	// threshhold in between scroll events
	private final int T_SCROLL = 500;

	//
	private Handler toAdd;
	private String toHandler;
	private boolean isAdding;

	// number of records gathered before writing to file
	private final int TREE_RECORD_THRESHOLD = 50;
	private final int IO_RECORD_THRESHOLD = 250;
	private final int TEXT_RECORD_THRESHOLD = 150;

	private long ioTreshold = 0;

	private static final String folderName = Environment
			.getExternalStorageDirectory().toString() + "/TBB";

	private static int preFileSeq;
	private static String adjusting = "";

	private static boolean loggingEnabled;

	private static String lastTree = "";
	private static String lastIO = "";
	private static String lastText = "";
	private static String lastAudio = "";
	private static String extension = ".zip";

	private SharedPreferences sharedPref;

	private static boolean firstConnect = true;

	/**
	 * Initialises the logger
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

		if (intent.getAction().equals("mswat_screen_on") && !loggingEnabled) {
			loggingEnabled = true;
			File folder = new File(folderName);
			if (!folder.exists()) {
				folder.mkdir();
			}

			preFileSeq = sharedPref.getInt("preFileSeq", 0);
			SharedPreferences.Editor editor = sharedPref.edit();
			editor.putInt("preFileSeq", preFileSeq + 1);
			editor.commit();
			if (firstConnect) {
				firstConnect = false;
				editor.putString("pathTBB", folderName);
				editor.commit();
			}

			adjust(preFileSeq);
			lastTree = folderName + "/" + adjusting + preFileSeq + "_TREE.txt";
			lastIO = folderName + "/" + adjusting + preFileSeq + "_IO.txt";
			lastText = folderName + "/" + adjusting + preFileSeq + "_TEXT.txt";
			lastAudio = folderName + "/Audio";
			editor = sharedPref.edit();
			editor.putString("fileID", adjusting + (preFileSeq));
			editor.commit();
			// touch recogniser
			tpr = CoreController.getActiveTPR();
			while (tpr == null) {
				SystemClock.sleep(500);
				tpr = CoreController.getActiveTPR();

			}
//			Log.d(LT, "LBB init");

			// context used to write to file
			this.context = context;

			// register logger to receive messages from the core controller
			CoreController.registerLogger(this);

			// registers receivers
			ioID = registerIOReceiver();

			// starts monitoring touchscreen
			touchDevice = CoreController.monitorTouch();

			// hard coded, check device name with CoreController.getDevices and
			// see what driver u want to monitor
			devSpecialKeys = 8;
			devHomeAndVolume = 7;

			// monitor devices
			CoreController.commandIO(CoreController.MONITOR_DEV,
					devSpecialKeys, true);
			CoreController.commandIO(CoreController.MONITOR_DEV,
					devHomeAndVolume, true);

			eventRegisterID = registerEventReceiver();
			treeToLog = new ArrayList<String>();

			toAdd = new Handler();
		} else {
			if (loggingEnabled) {
				sincronize();
				loggingEnabled = false;
				
				CoreController.commandIO(CoreController.MONITOR_DEV,
						devSpecialKeys, false);
				CoreController.commandIO(CoreController.MONITOR_DEV,
						devHomeAndVolume, false);
				CoreController.commandIO(CoreController.MONITOR_DEV,
						touchDevice, false);
				
				CoreController.unregisterIOReceiver(ioID);
				CoreController.unregisterEvent(eventRegisterID);
				// Log.d(LT, "Unregistered");

				File text = new File(lastText);
				File tree = new File(lastTree);
				File io = new File(lastIO);
				File audio = new File(lastAudio);

				Zipping zp;
				ArrayList<String> toZipList = new ArrayList<String>();
				toZipList.add(lastTree);
				toZipList.add(lastIO);

				// Log.d("gcs", folderName + "/" + adjusting + preFileSeq +
				// ".zip");
				if (text.exists()) {
					toZipList.add(lastText);
				}
				if (audio.exists()) {
					String[] audioFiles = audio.list();
					for (int i = 0; i < audioFiles.length; i++)
						toZipList.add(folderName + "/Audio/" + audioFiles[0]);
					extension = "_A"+extension;
				}
				String[] toZip = new String[toZipList.size()];
				toZip = toZipList.toArray(toZip);
				zp = new Zipping(toZip, (folderName + "/" + adjusting
						+ preFileSeq + extension));
				if (zp.zip()) {
					text.delete();
					tree.delete();
					io.delete();
					DeleteRecursive(audio);
					CloudStorage.cloudSinc(context);
					extension = ".zip";

				}

			}
		}
	}

	private void DeleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				DeleteRecursive(child);

		fileOrDirectory.delete();
	}

	private void adjust(int preFileSeq2) {

		if (preFileSeq2 < 10) {
			adjusting = "0000";
		} else if (preFileSeq2 < 100) {
			adjusting = "000";
		} else if (preFileSeq2 < 1000)
			adjusting = "00";
		else if (preFileSeq2 < 10000)
			adjusting = "0";

	}

	private void sincronize() {
		registerToLog(ioToLog, folderName + "/" + adjusting + preFileSeq
				+ "_IO.txt", true);
		ioToLog = new ArrayList<String>();
		registerToLog(treeToLog, folderName + "/" + adjusting + preFileSeq
				+ "_TREE.txt", true);
		treeToLog = new ArrayList<String>();
		registerToLog(textLog, folderName + "/" + adjusting + preFileSeq
				+ "_TEXT.txt", true);
		textLog = new ArrayList<String>();

	}

	@Override
	public int registerIOReceiver() {
		return CoreController.registerIOReceiver(this);
	}

	@Override
	public int registerEventReceiver() {
		return CoreController.registerEventReceiver(this);
	}

	@Override
	public void onUpdateIO(int device, int type, int code, int value,
			int timestamp) {

		if (this.touchDevice == device) {
			int touchType;
			if ((touchType = tpr.identifyOnChange(type, code, value, timestamp)) != -1) {
				TouchEvent te = tpr.getlastTouch();
				ioTreshold = timestamp;
				ioToLog.add(id + "," + device + " " + touchType + ","
						+ te.getIdentifier() + "," + te.getX() + ","
						+ te.getY() + "," + te.getPressure() + ","
						+ te.getTime());
			}
			if (ioToLog.size() > IO_RECORD_THRESHOLD) {
				// Log.d(LT, toLog.toString());
				registerToLog(ioToLog, folderName + "/" + adjusting
						+ preFileSeq + "_IO.txt", true);
				ioToLog = new ArrayList<String>();

			}
		} else {
			if (type != 0) {
				ioToLog.add(id + "," + device + "," + type + "," + code + ","
						+ value + "," + timestamp);
			}

		}
	}

	@Override
	public void onUpdateEvent(AccessibilityEvent event) {
		int evType = event.getEventType();

		if (event.getEventTime() < ioTreshold) {
			return;
		}
		AccessibilityNodeInfo parent = AccessibilityScrapping
				.getRootParent(event.getSource());
		if (parent == null) {
			return;

		}

		String result = AccessibilityScrapping.getChildren(parent, 0);

		StringBuilder sb = new StringBuilder();
		sb.append(event.getEventType() + "!_!"
				+ AccessibilityScrapping.hashIt(event.getSource()) + "!_!"
				+ event.getEventTime() + "\n");
		sb.append(event.getSource().toString() + "\n");

		if (result.hashCode() != lastRead || lastRead == -1) {
			id++;
			sb.append(parent.getClassName() + "\n");
			sb.append(AccessibilityScrapping.getCurrentActivityName(context)
					+ "\n");
			sb.append((id) + "\n");
			sb.append("{" + AccessibilityScrapping.getDescription(parent)
					+ result + "\n}");

			if (!isAdding) {
				isAdding = true;
				toAdd.postDelayed(new Runnable() {
					public void run() {
						treeToLog.add(toHandler);
						isAdding = false;
					}
				}, 200);
			}

			toHandler = sb.toString();
			lastRead = result.hashCode();
		}

		if (treeToLog.size() > TREE_RECORD_THRESHOLD) {

			registerToLog(treeToLog, folderName + "/" + adjusting + preFileSeq
					+ "_TREE.txt", true);
			treeToLog = new ArrayList<String>();
		}

	}

	/**
	 * Write to log file
	 * 
	 * @param message
	 */
	public void registerToLog(ArrayList<String> message, String filepath,
			boolean sinc) {
		// Log.d(LT, "Writing to:" + filepath);
		// Log.d(LT, "Writing: " + message.toString());

		LogToFile task = new LogToFile(context, message, filepath, sinc);
		task.execute();

	}

	/**
	 * Write the string record into the log file
	 * 
	 * @param record
	 * @return
	 */
	public boolean logKeystroke(String record) {
		// Log.d(LT, "rec:" + record);

		if (textLog.size() > TEXT_RECORD_THRESHOLD) {
			// Log.d(LT, "rec:" + record);
			registerToLog(textLog, folderName + "/" + adjusting + preFileSeq
					+ "_TEXT.txt", true);

			textLog = new ArrayList<String>();
		}

		return textLog.add(record + "/n");
	}

	/**
	 * Writes to log file the last touch
	 */
	public static class LogToFile extends AsyncTask<Void, Void, Void> {

		private Context myContextRef;
		private ArrayList<String> text;
		private String filepath;
		private boolean sinc;

		public LogToFile(Context context, ArrayList<String> message,
				String filepath, boolean sinc) {
			this.myContextRef = context;
			text = message;
			this.filepath = filepath;
			this.sinc = sinc;
			if (sinc) {
				File file = new File(filepath);
				FileWriter fw;

				try {
					fw = new FileWriter(file, true);
					for (int i = 0; i < text.size(); i++) {
						fw.write(text.get(i) + "\n");
					}
					fw.close();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		@Override
		protected Void doInBackground(Void... params) {
			if (sinc)
				return null;
			File file = new File(filepath);
			FileWriter fw;

			try {
				fw = new FileWriter(file, true);
				for (int i = 0; i < text.size(); i++) {
					fw.write(text.get(i) + "\n");
				}
				fw.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;

		}
	}



	public int getIdSequence() {
		return preFileSeq;
	}

	@Override
	public int[] getType() {
		int[] type = new int[4];
		type[0] = AccessibilityEvent.TYPE_VIEW_CLICKED;
		type[1] = AccessibilityEvent.TYPE_VIEW_SCROLLED;
		type[2] = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
		type[3] = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

		return type;
	}

}
