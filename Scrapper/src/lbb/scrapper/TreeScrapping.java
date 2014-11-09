package lbb.scrapper;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import mswat.core.CoreController;
import mswat.core.activityManager.scrapper.Tree;
import mswat.interfaces.ContentReceiver;
import mswat.interfaces.IOReceiver;

public class TreeScrapping extends BroadcastReceiver implements
		ContentReceiver, IOReceiver {
	private final static String LT = "scrapper";
	private ArrayList<Tree> trees;
	private TreeNavigation t_nav;
	private boolean status;
	private static boolean first;
	
	 static int idIO;
	 static int idContent; 


	@Override
	public int registerContentReceiver() {

		return CoreController.registerContentReceiver(this);
	}

	@Override
	public void onUpdateContent(Tree newTree) {
		if (status) {
			if (first) {
				t_nav = new TreeNavigation(newTree);
				first = false;
			} else {

				if (duplicate(newTree) == -1) {
					trees.add(newTree);
				}
//				Log.d(LT, "------------------------\n" + newTree.toString());
				t_nav.navNext(newTree,false);
			}
		}
	}

	private int duplicate(Tree newTree) {
		for (int i = 0; i < trees.size(); i++) {
			if (trees.get(i).equals(newTree))
				return i;
		}
		return -1;
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		trees = new ArrayList<Tree>();
		first = true;
		idContent = registerContentReceiver();
		idIO = registerIOReceiver();
		CoreController.commandIO(CoreController.MONITOR_DEV, 7, true);
		CoreController.commandIO(CoreController.SET_BLOCK, 7, true);

	}

	@Override
	public int registerIOReceiver() {
		return CoreController.registerIOReceiver(this);
	}

	@Override
	public void onUpdateIO(int device, int type, int code, int value,
			int timestamp) {
		if (code == 115 && value == 0) {
			status = true;
		} else {
			if (code == 114 && value == 0) {
				status = false;
			}

		}
	}
}
