package mswat.core.logger;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class AccessibilityScrapping {

	public static int hashIt(AccessibilityNodeInfo n) {
		String s = "" + n.getPackageName() + n.getClassName() + n.getText()
				+ n.getContentDescription() + n.getActions();
		return s.hashCode();
	}

	public static String getCurrentActivityName(Context context) {
		ActivityManager activityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		if (activityManager == null)
			return "";
		ActivityManager.RunningTaskInfo info = activityManager.getRunningTasks(
				1).get(0);
		ComponentName topActivity = info.topActivity;
		return topActivity.getClassName();
	}

	/**
	 * Get root parent from node source
	 * 
	 * @param source
	 * @return
	 */
	static AccessibilityNodeInfo getRootParent(AccessibilityNodeInfo source) {
		AccessibilityNodeInfo current = source;
		if (current != null)
			while (current.getParent() != null) {
				AccessibilityNodeInfo oldCurrent = current;
				current = current.getParent();
				oldCurrent.recycle();
			}
		return current;
	}

	static String getChildren(AccessibilityNodeInfo node, int childLevel) {
		StringBuilder sb = new StringBuilder();
		if (node.getChildCount() > 0) {
			sb.append("{");
			for (int i = 0; i < node.getChildCount(); i++) {
				if (node.getChild(i) != null) {
					if (i > 0)
						sb.append("!_!");
					sb.append(getDescription(node.getChild(i)));

					if (node.getChild(i).getChildCount() > 0)
						sb.append(getChildren(node.getChild(i), childLevel + 1));
				}

			}
			sb.append("}");
		}

		return sb.toString();
	}

	static String getDescription(AccessibilityNodeInfo n) {

		// if (n.getText() != null)
		// return hashIt(n) + "," + n.getText();
		// else
		// return hashIt(n) + "," + n.getContentDescription()
		String[] unhandled = n.toString().split(";");
		String text = "" + n.getText();
		String description = "" + n.getContentDescription();
		String textE = "null";
		String descE = "null";

		try {
			if (!text.equals(textE)) {
				textE = Base64.encodeBase64String(Encryption.encrypt(text));
				textE = text;
			}
			if (!description.equalsIgnoreCase(descE)) {
				descE = Base64.encodeBase64String(Encryption
						.encrypt(description));
				// descE = description;

			}
			// DECRYPTION
			// Encryption.decrypt( Base64.decodeBase64(test)));
		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String result = unhandled[0];
		for (int i = 1; i < 5; i++) {
			result += "!;!" + unhandled[i];
		}
		result += "!;!" + textE;
		result += "!;!" + descE;
		for (int i = unhandled.length - 11; i < unhandled.length; i++) {
			result += "!;!" + unhandled[i];
		}
		return hashIt(n) + "," + result;
	}
}
