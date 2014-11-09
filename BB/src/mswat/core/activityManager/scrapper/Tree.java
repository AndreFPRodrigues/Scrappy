package mswat.core.activityManager.scrapper;

import java.util.ArrayList;

import mswat.core.CoreController;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

@SuppressLint("NewApi")
public class Tree {
	private final static String LT = "scrapper";

	private String description;
	private String debugDesc;

	private ArrayList<AccessibilityNodeInfo> interactive;
	private ArrayList<Boolean> scrolling;
	private String toHash;
	private int interactiveTreeLink[];
	private boolean scrollEnabled;

	public Tree(AccessibilityNodeInfo parent) {
		toHash = "";
		scrollEnabled=true;
		debugDesc = "";
		interactive = new ArrayList<AccessibilityNodeInfo>();
		scrolling = new ArrayList<Boolean>();

		description = "{" + getDescription(parent) + getChildren(parent, 0)
				+ "\n";

		interactiveTreeLink = new int[interactive.size()];

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

	String getChildren(AccessibilityNodeInfo node, int childLevel) {
		StringBuilder sb = new StringBuilder();
		if (node.getChildCount() > 0) {
			sb.append("{");
			debugDesc += "{";
			for (int i = 0; i < node.getChildCount(); i++) {
				if (node.getChild(i) != null) {
					if (i > 0)
						sb.append("!_!");

					AccessibilityNodeInfo child = node.getChild(i);
					// check interactive
					if (checkInteractive(child))
						debugDesc += "*";

					sb.append(getDescription(child));

					if (node.getChild(i).getChildCount() > 0)
						sb.append(getChildren(node.getChild(i), childLevel + 1));

				}

			}
			sb.append("}");
			debugDesc += "}";

		}
		return sb.toString();
	}

	public boolean click(AccessibilityNodeInfo click) {
		// Log.d(LT, "Click Redo:" + click.toString());
		if (click != null)
			return click.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		return false;
	}
	public boolean scroll(AccessibilityNodeInfo scroll) {
		// Log.d(LT, "Click Redo:" + click.toString());
		if (scroll != null)
			return scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
		return false;
	}
	
	public boolean scrollState(){
		return scrollEnabled;
	}
	
	public ArrayList<Boolean > getScrollingStates(){
		return scrolling;
	}
	
	public void setScrollingStates( ArrayList<Boolean >  sc){
		scrolling=sc;
	}
	
	
	
	public void disableScrolls(){
		scrollEnabled=false;
	}

	public AccessibilityNodeInfo scrollNext() {
		for (int i = 0; i < scrolling.size(); i++) {
			if (scrolling.get(i)) {
				scrolling.set(
						i,
						interactive.get(i).performAction(
								AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
				return interactive.get(i);
			}
		}
		return null;
	}

	public ArrayList<AccessibilityNodeInfo> getInteractiveNodes() {
		return interactive;
	}

	@SuppressLint("NewApi")
	private boolean checkInteractive(AccessibilityNodeInfo child) {
		if (!child.isCheckable() && child.isClickable()
				&& child.isVisibleToUser() /*
											 * && child.isEnabled()&&
											 * child.isFocusable()
											 */) {
			scrolling.add(child.isScrollable());
			return interactive.add(child);
		}
		return false;

	}

	public String getDescription(AccessibilityNodeInfo n) {

		// if (n.getText() != null)
		// return hashIt(n) + "," + n.getText();
		// else
		// return hashIt(n) + "," + n.getContentDescription()
		String[] unhandled = n.toString().split(";");
		String text = "" + n.getText();
		String description = "" + n.getContentDescription();
		String textE = text;
		String descE = description;
		debug(text, description, n);
		String result = unhandled[0];
		for (int i = 1; i < 5; i++) {
			result += "!;!" + unhandled[i];
		}
		result += "!;!" + textE;
		result += "!;!" + descE;
		for (int i = unhandled.length - 11; i < unhandled.length; i++) {
			result += "!;!" + unhandled[i];
		}

		toHash += /* unhandled[2] + */unhandled[3] + unhandled[4] + textE + descE
		/* + unhandled[17] */;
		;

		return result;
	}

	public AccessibilityNodeInfo navNext(int index) {
		if (interactive.size() > index) {
			int threshold = 0;
			Rect outBounds = new Rect();
			interactive.get(index).getBoundsInScreen(outBounds);

			// manhoso tirar o || que esta a tirar o ghots node na app e-mail
			if (!onScreenBounds(outBounds)
					|| (index == 0 && !interactive.get(index).isFocusable())) {
//				Log.d(LT,
//						"NEXT no Intersect: "
//								+ readableText(interactive.get(index)));
				return interactive.get(index);
			}
			Log.d(LT, "NEXT: " 
					+ readableText(interactive.get(index))/*
														 * +
														 * " "+interactive.get(
														 * index).toString() +
														 * " intSize:" +
														 * interactive.size() +
														 * " index:" + index
														 */);
			while (!interactive.get(index).performAction(
					AccessibilityNodeInfo.ACTION_CLICK)
					&& threshold < 30) {
				threshold++;
			}

			return interactive.get(index);
		} else {
			return null;
		}

	}



	public boolean onScreenBounds(Rect bounds) {
//		Log.d(LT, "bounds:" + bounds.left + " " + bounds.bottom + " "
//				+ bounds.right + " " + bounds.top);
//		Log.d(LT, "screen:" + 0 + " " + CoreController.M_HEIGHT + " "
//				+ CoreController.M_WIDTH + " " + 0);
		Rect screen = new Rect(0, 0, (int) CoreController.M_WIDTH,
				(int) CoreController.M_HEIGHT);
		return screen.intersect(bounds);

	}

	public void addTreeLink(int interactiveIndex, int hash) {
		if (interactive.size() > interactiveIndex) {
			interactiveTreeLink[interactiveIndex] = hash;
		}

	}

	private String readableText(AccessibilityNodeInfo n) {
		if (n != null) {
			if (n.getText() == null) {
				if (n.getContentDescription() == null) {
					if (n.getChildCount() > 0)
						return readableText(n.getChild(0));

				} else {
					return n.getContentDescription() + "";
				}
			} else {
				return "" + n.getText();
			}
			return n.getClassName() + "";

		}
		return "null";

	}

	private void debug(String text, String description2,
			AccessibilityNodeInfo node) {
		if (text.equals("null")) {
			if (description2.equals("null")) {
				debugDesc += node.getClassName() + "\n";
			} else {
				debugDesc += description2 + "\n";
			}
		} else {
			debugDesc += text + "\n";

		}
	}

	public String getFullDescription() {
		return toHash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Tree other = (Tree) obj;
		if (hashCode() != other.hashCode())
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		return toHash.hashCode();
	}

	@Override
	public String toString() {
		// return description;
		return debugDesc;
	}

}
