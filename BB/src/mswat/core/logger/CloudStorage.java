package mswat.core.logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings.System;
import android.text.format.Time;
import android.util.Log;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;

/**
 * Simple wrapper around the Google Cloud Storage API
 */
public class CloudStorage {

	private static Properties properties;
	private static Storage storage;

	private static final String PROJECT_ID_PROPERTY = "project.id";
	private static final String APPLICATION_NAME_PROPERTY = "application.name";
	private static final String ACCOUNT_ID_PROPERTY = "account.id";
	private static final String PRIVATE_KEY_PATH_PROPERTY = "private.key.path";

	private static long lastSync = 0;
	
	private final static long syncThreshold= 86400000;

	/**
	 * Uploads a file to a bucket. Filename and content type will be based on
	 * the original file.
	 * 
	 * @param bucketName 
	 *            Bucket where file will be uploaded
	 * @param filePath
	 *            Absolute path of the file to upload
	 * @throws Exception
	 */
	public static void uploadFile(String bucketName, String filePath)
			throws Exception {

		Storage storage = getStorage();

		StorageObject object = new StorageObject();
		object.setBucket(bucketName);

		File file = new File(filePath);

		InputStream stream = new FileInputStream(file);
		try {
			String contentType = URLConnection
					.guessContentTypeFromStream(stream);
			InputStreamContent content = new InputStreamContent(contentType,
					stream);

			Storage.Objects.Insert insert = storage.objects().insert(
					bucketName, null, content);
			insert.setName(file.getName());

			insert.execute();
		} finally {
			stream.close();
		}
	}

	public static void downloadFile(String bucketName, String fileName,
			String destinationDirectory) throws Exception {

		File directory = new File(destinationDirectory);
		if (!directory.isDirectory()) {
			throw new Exception(
					"Provided destinationDirectory path is not a directory");
		}
		File file = new File(directory.getAbsolutePath() + "/" + fileName);

		Storage storage = getStorage();

		Storage.Objects.Get get = storage.objects().get(bucketName, fileName);
		FileOutputStream stream = new FileOutputStream(file);
		try {
			get.executeAndDownloadTo(stream);
		} finally {
			stream.close();
		}
	}

	/**
	 * Deletes a file within a bucket
	 * 
	 * @param bucketName
	 *            Name of bucket that contains the file
	 * @param fileName
	 *            The file to delete
	 * @throws Exception
	 */
	public static void deleteFile(String bucketName, String fileName)
			throws Exception {

		Storage storage = getStorage();

		storage.objects().delete(bucketName, fileName).execute();
	}

	/**
	 * Creates a bucket
	 * 
	 * @param bucketName
	 *            Name of bucket to create
	 * @throws Exception
	 */
	public static void createBucket(String bucketName) throws Exception {

		Storage storage = getStorage();

		Bucket bucket = new Bucket();
		bucket.setName(bucketName);

		storage.buckets()
				.insert(getProperties().getProperty(PROJECT_ID_PROPERTY),
						bucket).execute();
	}

	/**
	 * Deletes a bucket
	 * 
	 * @param bucketName
	 *            Name of bucket to delete
	 * @throws Exception
	 */
	public static void deleteBucket(String bucketName) throws Exception {

		Storage storage = getStorage();

		storage.buckets().delete(bucketName).execute();
	}

	/**
	 * Lists the objects in a bucket
	 * 
	 * @param bucketName
	 *            bucket name to list
	 * @return Array of object names
	 * @throws Exception
	 */
	public static List<String> listBucket(String bucketName) throws Exception {

		Storage storage = getStorage();

		List<String> list = new ArrayList<String>();

		List<StorageObject> objects = storage.objects().list(bucketName)
				.execute().getItems();
		if (objects != null) {
			for (StorageObject o : objects) {
				list.add(o.getName());
			}
		}

		return list;
	}

	/**
	 * List the buckets with the project (Project is configured in properties)
	 * 
	 * @return
	 * @throws Exception
	 */
	public static List<String> listBuckets() throws Exception {

		Storage storage = getStorage();

		List<String> list = new ArrayList<String>();

		List<Bucket> buckets = storage.buckets()
				.list(getProperties().getProperty(PROJECT_ID_PROPERTY))
				.execute().getItems();
		if (buckets != null) {
			for (Bucket b : buckets) {
				list.add(b.getName());
			}
		}

		return list;
	}

	private static Properties getProperties() throws Exception {

		if (properties == null) {
			properties = new Properties();
			InputStream stream = CloudStorage.class
					.getResourceAsStream("/cloudstorage.properties");
			try {
				properties.load(stream);
			} catch (IOException e) {
				throw new RuntimeException(
						"cloudstorage.properties must be present in classpath",
						e);
			} finally {
				stream.close();
			}
		}
		return properties;
	}

	private static final String KEY_TYPE = "PKCS12";
	private static final String KEY_ALIAS = "privatekey";
	private static final String KEY_PASSWORD = "notasecret";

	private static Storage getStorage() throws Exception {

		if (storage == null) {

			HttpTransport httpTransport = new NetHttpTransport();
			JsonFactory jsonFactory = new JacksonFactory();

			List<String> scopes = new ArrayList<String>();
			scopes.add(StorageScopes.DEVSTORAGE_FULL_CONTROL);

			KeyStore keyStore = KeyStore.getInstance(KEY_TYPE);
			InputStream keyStream = CloudStorage.class
					.getResourceAsStream("/key.p12");
			PrivateKey privateKey = PrivateKeys.loadFromKeyStore(keyStore,
					keyStream, KEY_PASSWORD, KEY_ALIAS, KEY_PASSWORD);

			Credential credential = new GoogleCredential.Builder()
					.setTransport(httpTransport)
					.setJsonFactory(jsonFactory)
					.setServiceAccountId(
							getProperties().getProperty(ACCOUNT_ID_PROPERTY))
					.setServiceAccountPrivateKey(
					/*
					 * new File(Environment.getExternalStorageDirectory()
					 * .toString() + getProperties().getProperty(
					 * PRIVATE_KEY_PATH_PROPERTY))
					 */privateKey).setServiceAccountScopes(scopes).build();

			storage = new Storage.Builder(httpTransport, jsonFactory,
					credential).setApplicationName(
					getProperties().getProperty(APPLICATION_NAME_PROPERTY))
					.build();
		}

		return storage;
	}

	public static void cloudSinc(final Context c) {
		Time now = new Time();
		now.setToNow();

		if (Math.abs(lastSync - now.toMillis(false)) < syncThreshold)
			return;

		if (isNetworkConnected(c)) {

//			 Log.d("gcs", "STORING");
			Thread b = new Thread(new Runnable() {

				public void run() {
					try {
						String bucket = CloudStorage.checkBucket(c);
						CloudStorage.sincFiles(bucket);
						CloudStorage.uploadFiles(bucket);
						Time now = new Time();
						now.setToNow();
						lastSync = now.toMillis(false);

					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			b.start();
		}
	}

	protected static String checkBucket(Context context) throws Exception {
		AccountManager manager = (AccountManager) context
				.getSystemService(context.ACCOUNT_SERVICE);
		Account[] list = manager.getAccounts();
		List<String> buckets = CloudStorage.listBuckets();
		if (list.length > 0) {
			String buck = "tbb_" + ((list[0].name.split("@"))[0]);
			for (String b : buckets) {
				if (b.equalsIgnoreCase(buck)) {
					return buck;
				}
			}
			CloudStorage.createBucket(buck);
			return buck;
		}
		return null;
	}

	protected static void uploadFiles(String bucket) throws Exception {

		String[] zips = (new File(Environment.getExternalStorageDirectory()
				.toString() + "/TBB")).list();
		String[] zipFilesPath = new String[zips.length];
		String audio="";
		for (int i = 0; i < zips.length; i++) {
			// Log.d("gcs", "Uploading:"
			// + Environment.getExternalStorageDirectory().toString()
			// + "/TBB/" + zips[i]);

			zipFilesPath[i] = Environment.getExternalStorageDirectory()
					.toString() + "/TBB/" + zips[i];
			if(zips[i].contains("_A"))
				audio="_A";
		}

		Zipping zp = new Zipping(zipFilesPath, (Environment
				.getExternalStorageDirectory().toString()
				+ "/TBB/"
				+ zips[zips.length - 1] +audio+ ".zip"));

		if (zp.zip()) {
			CloudStorage.uploadFile(bucket, Environment
					.getExternalStorageDirectory().toString()
					+ "/TBB/"
					+ zips[zips.length - 1]+ audio+ ".zip");
		}
	}

	protected static void sincFiles(String bucket) throws Exception {

		List<String> files = CloudStorage.listBucket(bucket);
		if (files.size() == 0) {
			return;
		}
		String[] zips = (new File(Environment.getExternalStorageDirectory()
				.toString() + "/TBB")).list();
		Arrays.sort(zips);
		for (int j = 0; j < zips.length; j++) {
			// Log.d("gcs", zips[j]);

			if (zips[j].equals(files.get(files.size() - 1))) {
				for (int k = 0; k < j + 1; k++) {
					// Log.d("gcs", "to delete:" + zips[k]);

					File del = new File(Environment
							.getExternalStorageDirectory().toString()
							+ "/TBB/"
							+ zips[k]);
					del.delete();

				}
				return;
			}

		}

	}

	private static boolean isNetworkConnected(Context c) {
		ConnectivityManager cm = (ConnectivityManager) c
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		return cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
	}
}
