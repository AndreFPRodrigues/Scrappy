package lbb.scrapper;

import java.util.ArrayList;

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class Transitions {
	private final static String LT = "scrapper";

	private ArrayList<Transition> trt;

	public Transitions() {
		trt = new ArrayList<Transition>();
	}

	public void createTransition(int origin, AccessibilityNodeInfo node,
			int destiny, boolean scrollInteraction) {
		trt.add(new Transition(origin, node, destiny, scrollInteraction));
	}

	class Transition {
		int origin;
		int destiny;
		boolean isScroll;
		AccessibilityNodeInfo path;
		float x;
		float y;

		Transition(int origin, AccessibilityNodeInfo node, int destiny,
				boolean scrollInteraction) {
			this.origin = origin;
			path = node;
			this.destiny = destiny;
			Rect aux = new Rect();
			isScroll = scrollInteraction;
			path.getBoundsInScreen(aux);
			x = aux.exactCenterX();
			y = aux.exactCenterY();

		}

		boolean isTransition(int ori, int dest) {
			return (ori == origin && destiny == dest);
		}

		boolean isScroll() {
			return isScroll;
		}

		AccessibilityNodeInfo getNode() {
			return path;
		}

		public float[] getBounds() {
			return new float[] { x, y };
		}

	}

	public Transition getNode(int ori, int dest) {
		// Log.d(LT, "o:" + ori + " d:" + dest);
		for (int i = 0; i < trt.size(); i++) {
			if (trt.get(i).isTransition(ori, dest)) {
				// Log.d(LT, ori + "->" + dest + trt.get(i).path.getText() + " "
				// + trt.get(i).path.getContentDescription());
				return trt.get(i);
			}
		}
		return null;
	}

	public float[] getBounds(int ori, int dest) {
		for (int i = 0; i < trt.size(); i++) {
			if (trt.get(i).isTransition(ori, dest)) {
				return new float[] { trt.get(i).x, trt.get(i).y };

			}
		}
		return null;
	}

	public void removeAllTo(int index) {
		// Log.d(LT, "REMOVED ALL " + index + " s:" + trt.size());
		int size = trt.size();
		for (int i = 0; i < size; i++) {
			if (trt.get(i).origin == index || trt.get(i).destiny == index) {
				trt.remove(i);
				size--;
				i--;
			}
		}

	}

	public int scrollFrom(int index , int result_aux) {
		int size = trt.size();
		int result = -1;
		for (int i = 0; i < size; i++) {
			if (trt.get(i).isScroll) {
				if (trt.get(i).origin == index) {
					trt.remove(i);
					size--; 
					i--;
				}
				if (trt.get(i).destiny == index) {
					result = trt.get(i).origin;
					trt.remove(i);
					size--;
					i--;
				}
			}
		}
		if(result!=-1)
			return scrollFrom(index-1, result);
		
		return result_aux;

	}

}
