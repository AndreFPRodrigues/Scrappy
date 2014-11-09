package lbb.scrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import lbb.scrapper.Transitions.Transition;
import mswat.core.CoreController;
import mswat.core.activityManager.scrapper.Tree;

public class TreeNavigation {
	private final static String LT = "scrapper";

	private final int DEPTH = 3;
	private int currentDepth;
	private ArrayList<Tree> baseline;
	private ArrayList<Tree> legacy;

	private ArrayList<Integer> id_interaction;

	// records the transitions nodes between baseline trees
	private Transitions transitions;

	// activity being explored
	private String activity;

	// redo steps when a back goes more than 1 window back
	private boolean toRedo;
	// when redoing more than 1 step we ignore changes in the trees
	private boolean ignore;

	private int backTo;
	private boolean isBacking;
	private boolean isBackingSameActivity;

	private boolean isBackingLegacyTree;

	private boolean isNext;
	private ArrayList<AccessibilityNodeInfo> previousNodes;
	private boolean scrollInteraction;
	private ArrayList<Rect> previousNodesRect;

	private AccessibilityNodeInfo lastClick;

	private boolean terminated;

	// delay tree update
	private Handler performNext;
	private int timeout = 5000;
	private int numberOfNext;

	// stats
	private int unsuccessfulBacks;
	private int redos;
	private int twoStepRedos;
	private int backThroughInterface;

	private int debugAHAH = 0;

	public TreeNavigation(Tree t) {
		activity = CoreController.getCurrentActivityName();
		Log.d(LT, "------------------------\n" + t.toString()
				+ "\n---------------------");
		currentDepth = 0;
		numberOfNext = 0;
		baseline = new ArrayList<Tree>();
		legacy = new ArrayList<Tree>();
		id_interaction = new ArrayList<Integer>();
		transitions = new Transitions();
		previousNodes = new ArrayList<AccessibilityNodeInfo>();
		baseline.add(t);
		performNext = new Handler();
		// for (int i = 0; i < t.getInteractiveNodes().size(); i++)
		id_interaction.add(0);
		navNext(t, true);
	}

	public synchronized void navNext(Tree t, boolean forced) {
//		Log.d(LT, "STARTNG NEXT: " + currentDepth + " " + isBacking + " "
//				+ backTo + " " + baseline.size() + " " + id_interaction.size()
//				+ " f:" + forced);

		// check if we went outside the activity and back
		if (!sameActivity() && currentDepth > 0) {
//			Log.d(LT, "Returning to the application");
			isBacking = true;
			if (!isBackingSameActivity)
				backTo = currentDepth - 1;
			isBackingSameActivity = true;
			back();
			numberOfNext++;
			return;
		}

		// if we already navigate this tree from a previous node we back
		if (legacyTree(t) && currentDepth > 0) {
//			Log.d(LT, "Legacy");
			isBacking = true;
			if (!isBackingLegacyTree)
				backTo = currentDepth - 1;
			isBackingLegacyTree = true;
			back();
			numberOfNext++;
			return;
		}

		// detect if back was successful
		if (isBacking) {

			if (!achievedBack(t)) {
//				Log.d(LT, "Back Failed");
				unsuccessfulBacks++;
				back();
				return;
			} else {
//				Log.d(LT, "Back Successful");

				isBackingSameActivity = false;
				isBackingLegacyTree = false;
				currentDepth--;
			}
		}

		if (toRedo) {
			// redoing a step after a intended back that goes more than one tree
			// back
//			Log.d(LT, "Redoing after back: ");
			isBacking = false;
			redo(currentDepth, t);
			currentDepth++;
			redos++;
			if (backTo == currentDepth) {
				toRedo = false;
			} else {
				twoStepRedos++;
			}
			return;
		}

		int t_index;
		isBacking = false;

		// if we are in the root node and no more nodes are to be explored then
		// finish exploration
		if (t == null && currentDepth == 0) {
			finish();
			return;
		}
		// Check if the next was successful or if we pressed a back button on
		// the interface
		// If we detect the same we try to redo the second to last action to
		// return to the same window
		if ((t_index = duplicate(t)) != baseline.size() - 1 && t_index != -1
				&& previousNodes.size() > 1 && currentDepth > 0
				&& t_index < currentDepth) {
//			Log.d(LT, "Redoing:" + currentDepth + " " + t_index);
			currentDepth--;
			backThroughInterface++;
			redo(t_index, t);

			previousNodes.remove(previousNodes.size() - 1);
			return;
		}

		// detect if we have reached the depth desired and back if we did
		if (currentDepth >= DEPTH) {
			numberOfNext++;
			isBacking = true;
			backTo = currentDepth - 1;
			if (t_index == -1) {
				legacy.add(t);
			}

			back();
			return;
		}

		// verify if we are in a new tree and add it to the baseline to be
		// explored
		if (t_index == -1) {
//			Log.d(LT, "New Tree");
			// Tools.screenShot();
			AccessibilityNodeInfo pvn = previousNodes
					.get(previousNodes.size() - 1);

			// record the transitions that took us to the current tree (so we
			// can redo the action if needed)
			Rect r1 = new Rect();
			pvn.getBoundsInScreen(r1);
			transitions.createTransition(currentDepth - 1,
					previousNodes.get(previousNodes.size() - 1), currentDepth,
					scrollInteraction);
			scrollInteraction = false;
			t_index = baseline.size();
			baseline.add(t);
			id_interaction.add(0);
			if (isNext) {
				isNext = false;
			}
		} else {

			// if we are in the presence of a duplicate tree we replace our
			// current tree with it to ensure we can perform actions on the
			// latests nodes
			ArrayList<Boolean> scroll_aux = baseline.get(t_index)
					.getScrollingStates();
			baseline.set(t_index, t);
			baseline.get(t_index).setScrollingStates(scroll_aux);
		}

		// Navigate to next interactive item
		AccessibilityNodeInfo n;
		int indexInt;
		if ((n = baseline.get(t_index).navNext(
				(indexInt = id_interaction.get(t_index)))) == null) {
//			Log.d(LT, "Failed");
			// /*cd:" + currentDepth + " b:" + isBacking + " bT"
			// + backTo + " base:" + baseline.size() + " id:"
			// + id_interaction.size() + " f:" + forced + " t_ind:"
			// + t_index);*/
			// for (int i = 0; i < baseline.size(); i++) {
			// Log.d(LT, "index:" + i + " - " + id_interaction.get(i));
			// }
			if (baseline.get(t_index).scrollState()
					&& (n = t.scrollNext()) != null) {
				Log.d(LT, "Scrolling: " + t_index);

				previousNodes.add(n);
				scrollInteraction = true;
				;
				numberOfNext++;
				currentDepth++;

				setNextTimer(t, numberOfNext,
						n.getText() + " " + n.getContentDescription());

				return;
			} else {
				int from = transitions.scrollFrom(t_index, -1);
				baseline.get(t_index).disableScrolls();
//				Log.d(LT, "Disabling Scrolling: " + t_index + " " + from);

				if (from != -1){
					int aux=t_index-1;
					while(aux!=from){
						baseline.get(aux).disableScrolls();
						aux--;
					}
					backTo = from-1;
					isBacking = true;
					numberOfNext++; 
					updateBaseline(t_index);
					back();
					return;
				}

			}

			if (t_index > currentDepth
					&& baseline.get(currentDepth).getInteractiveNodes().size() > id_interaction
							.get(currentDepth)) {
//				Log.d(LT, "updating baseline after fail: " + t_index);

				backTo = t_index - 1;
				isBacking = true;
				numberOfNext++;
				updateBaseline(t_index);
				back();
				return;
			}

			// clear new trees from interactive nodes if more we dont want to
			// explore them
			int aux;
			if ((aux = baseline.size()) > DEPTH) {
//				Log.d(LT, "Size > depth:" + t_index);

				legacy.add(baseline.get(aux - 1));
				baseline.remove(aux - 1);
				id_interaction.remove(aux - 1);
			}

			// if we are at 0 depth we have no more nodes to explore
			// if we are at more it means we might have unexplored nodes higher
			// in the baseline tree
			if (currentDepth > 0) {
//				Log.d(LT, "Depth > 0:" + t_index);

				isBacking = true;
				backTo = currentDepth - 1;
				back();
				numberOfNext++;

			} else
				finish();
		} else {
			// add interactive node in case we go to a new tree and need to
			// record the action to redo
			previousNodes.add(n);
//			Log.d(LT, "Next Successfull " + t_index + " " + indexInt);

			numberOfNext++;
			id_interaction.set(t_index, indexInt + 1);
			isNext = true;
			currentDepth++;

			// Set timer to ensure if the next did trigger a change in the tree
			// we click the next interactive node in 3sec
			setNextTimer(t, numberOfNext,
					n.getText() + " " + n.getContentDescription());

		}

	}

	private boolean legacyTree(Tree t) {
		for (int i = 0; i < legacy.size(); i++) {
			if (legacy.get(i).equals(t)) {
				return true;
			}
		}
		return false;
	}

	private boolean updateBaseline(int t_index) {
		while (baseline.size() > t_index) {
			legacy.add(baseline.get(baseline.size() - 1));
			baseline.remove(baseline.size() - 1);
			id_interaction.remove(baseline.size());
			transitions.removeAllTo(baseline.size());
		}
		return true;
	}

	private void redo(int from, Tree t) {
		Transition trs = transitions.getNode(from, from + 1);
		if (trs != null) {
			AccessibilityNodeInfo n = getCorrenpondence(
					t.getInteractiveNodes(), trs.getNode(), trs.getBounds());
			if (!trs.isScroll())
				t.click(n);
			else
				t.scroll(n);
		}
	}

	private boolean sameActivity() {
//		Log.d(LT, "Act: " + CoreController.getCurrentActivityName() + " "
//				+ activity);
		return activity.equals(CoreController.getCurrentActivityName());
	}

	private void printArray(ArrayList<AccessibilityNodeInfo> previousNodes2) {
		for (int i = 0; i < previousNodes2.size(); i++) {
			// Log.d(LT, "PREVIOUS:  " + previousNodes2.get(i));
		}

	}

	private void setNextTimer(final Tree t, final int numberOfNext2,
			final String s) {
		performNext.postDelayed(new Runnable() {
			public void run() {
				if (numberOfNext2 == numberOfNext && !terminated) {
					isNext = false;
					currentDepth--;
					previousNodes.remove(previousNodes.size() - 1);
					// Log.d(LT, "Timer Activated " + s + " " + numberOfNext2
					// + " " + numberOfNext);

					navNext(t, true);
				} else {
					// Log.d(LT, "Timer Canceled " + s + " " + numberOfNext2 +
					// " "
					// + numberOfNext);

				}

			}
		}, timeout);

	}

	/**
	 * Get correspondent node in the current tree to the one want to press
	 * 
	 * @param t
	 * @param n
	 * @param bounds
	 * @return
	 */
	private AccessibilityNodeInfo getCorrenpondence(
			ArrayList<AccessibilityNodeInfo> t, AccessibilityNodeInfo n,
			float[] bounds) {

		for (int i = 0; i < t.size(); i++) {
			if (equalNodes(n, t.get(i), bounds)) {
				return t.get(i);
			}
		}
		return null;
	}

	/**
	 * Compare two accessibility nodes Compares description, text and class name
	 * If the text is null then it compares the screen bounds of the two nodes
	 * 
	 * @param n
	 * @param n1
	 * @param bounds
	 * @return
	 */
	private boolean equalNodes(AccessibilityNodeInfo n,
			AccessibilityNodeInfo n1, float[] bounds) {
		if (n != null
				&& n1 != null
				&& ((n.getText() == null && n1.getText() == null) || ("" + n
						.getText()).equals(("" + n1.getText())))
				&& ((n.getContentDescription() == null && n1
						.getContentDescription() == null) || ("" + n
						.getContentDescription()).equals(("" + n1
						.getContentDescription())))
				&& n.getClassName().equals(n1.getClassName())) {

			if (n.getText() == null || ("" + n.getText()).equals("null"))
				return checkBounds(bounds, n1);
			else
				return true;

		}
		return false;
	}

	private boolean checkBounds(float[] bounds, AccessibilityNodeInfo n1) {

		Rect r2 = new Rect();
		n1.getBoundsInScreen(r2);
		if (Math.abs(bounds[0] - r2.exactCenterX()) < 5
				&& Math.abs(bounds[1] - r2.centerY()) < 5) {
			return true;
		}
		return false;
	}

	private void finish() {
		Log.d(LT, "finished");
		terminated = true;
		CoreController.commandIO(CoreController.MONITOR_DEV, 7, false);
		CoreController.commandIO(CoreController.SET_BLOCK, 7, false);
		CoreController.unregisterContent(TreeScrapping.idContent);
		CoreController.unregisterIOReceiver(TreeScrapping.idIO);

	}

	private int duplicate(Tree t) {
		for (int i = 0; i < baseline.size(); i++) {
			if (baseline.get(i).equals(t)) {
				return i;
			}
		}
		return -1;
	}

	private void back() {
		while (!CoreController.back())
			;
	}

	private boolean achievedBack(Tree t) {
		// Log.d(LT, "Verify Back:" + backTo);
		while (backTo < (baseline.size() - 1)) {
			legacy.add(baseline.get(baseline.size() - 1));
			baseline.remove(baseline.size() - 1);
			id_interaction.remove(id_interaction.size() - 1);
			transitions.removeAllTo(baseline.size());
		}
		// Log.d(LT, "BT: " + baseline.size());
		// Log.d(LT, "CT: " + t.toString());

		if (t.equals(baseline.get(backTo))) {
			// Log.d(LT, "Same tree back sucessfull");
			isBacking = false;
			// currentDepth--;
			return true;
		} else {
			for (int i = 0; i < baseline.size(); i++) {
				if (t.equals(baseline.get(i))) {
					isBacking = false;
					currentDepth = i + 1;
					toRedo = true;
					return true;
				}
			}
		}

		return false;
	}

}
