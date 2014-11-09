package mswat.core.activityManager.scrapper;

import java.util.ArrayList;

import mswat.core.CoreController;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class TreeScrapper {

	private final static String LT = "scrapper";

	private long lastEvent;
	// private final int MAX_DEPTH = 1;
	// private int currentDepth;
	private Tree currentTree;
	// private ArrayList<Tree> trees;

	private int interactiveIndex = -1;

	//delay tree update
	private Handler toAdd; 
	private boolean isAdding; 
	private int refreshDelay=1500;

	public TreeScrapper() {
		// trees = new ArrayList<Tree>();
		currentTree = null;
		// currentDepth = 0;
		lastEvent = 0;
		toAdd = new Handler();

	}

	public void updateTree(AccessibilityEvent event) {	
		AccessibilityNodeInfo parent = Tree.getRootParent(event.getSource());
		if (parent == null) {
			return;
		}

		lastEvent = event.getEventTime();


		Tree result = new Tree(parent);

		 if (currentTree==null|| !currentTree.equals(result)) {		
			 currentTree = result;
			 if (!isAdding) {
					isAdding = true;
					toAdd.postDelayed(new Runnable() {
						public void run() {
							CoreController.updateContentReceivers(currentTree);
							isAdding = false;
						}
					}, refreshDelay);
				}
		 }else{
			

		 }
		// Log.d(LT, "New Tree");
		//
		// trees.add(result);
		// if (interactiveIndex != -1) {
		// currentTree.addTreeLink(interactiveIndex, result.getHash());
		// }
		// currentTree = result;
		// if (currentDepth < MAX_DEPTH) {
		// Log.d(LT, "Pressing");
		//
		// currentDepth++;
		// interactiveIndex = currentTree.pressNext();
		//
		// } else {
		//
		// currentDepth--;
		// interactiveIndex = -1;
		// Log.d(LT, "Backing:" + CoreController.back());
		// }
		// }
	}



}
