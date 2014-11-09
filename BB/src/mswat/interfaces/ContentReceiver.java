package mswat.interfaces;

import java.util.ArrayList;
import mswat.core.activityManager.scrapper.Tree;

public interface ContentReceiver {

	

	/**
	 * Registers receiver, returns the receiver identifier (index)
	 * 
	 * @return
	 */
	public int registerContentReceiver();

	/**
	 * Receives arrayList with the updated content
	 * 
	 * @param content
	 */
	public abstract void onUpdateContent(Tree content);


	

}
