package lbb.scrapper;

import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

public class Tools {
	static Process sh;
	static boolean firstTime = true;
	static OutputStream os;
	static int idScreenShot;
	static void screenShot() {

//		Log.d(LT, "ScreenShot:" + idScreenShot);

		try {
			if (firstTime) {
				sh = Runtime.getRuntime().exec("su", null, null);
				;
				firstTime = true;
			}

			os = sh.getOutputStream();

			os.write(("/system/bin/screencap -p " + "/sdcard/img"
					+ idScreenShot + ".png").getBytes("ASCII"));
			os.flush();
			os.close();
			
			idScreenShot++;
			// sh.waitFor();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
