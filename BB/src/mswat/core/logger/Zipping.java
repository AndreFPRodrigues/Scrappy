package mswat.core.logger;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipping {
	private static final int BUFFER = 2048;
	private final static String LT = "TBB";

	private String[] _files;
	private String _zipFile;

	public Zipping(String[] files, String zipFile) {
//		Log.d("gcs", zipFile);

		_files = files;
		_zipFile = zipFile;
	} 

	public boolean zip() {
		try {
			BufferedInputStream origin = null;
			FileOutputStream dest = new FileOutputStream(_zipFile);

			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
					dest));

			byte data[] = new byte[BUFFER];

			for (int i = 0; i < _files.length; i++) {
//				Log.d(LT, "Adding: " + _files[i]);
				FileInputStream fi = new FileInputStream(_files[i]);
				origin = new BufferedInputStream(fi, BUFFER);
				ZipEntry entry = new ZipEntry(_files[i].substring(_files[i]
						.lastIndexOf("/") + 1));
				out.putNextEntry(entry);
				int count;
				while ((count = origin.read(data, 0, BUFFER)) != -1) {
					out.write(data, 0, count);
				}
				origin.close();
			}

			out.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

}