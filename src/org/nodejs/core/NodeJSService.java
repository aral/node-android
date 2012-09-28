package org.nodejs.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class NodeJSService extends Service {

	private static final String TAG = "nodejs-service";
	private static final String NODEJS_PATH = "nodejs";
	private static final String DEFAULT_PACKAGE = "app.zip";

	private class NodeJSTask extends AsyncTask<String, Void, String> {

		Context mContext = null;

		NodeJSTask() {
			mContext = NodeJSService.this;
		}

		@Override
		protected String doInBackground(String... params) {
			// Copy files from assets to app path
			AssetManager assets = mContext.getAssets();

			File appPath = mContext.getDir(NODEJS_PATH, Context.MODE_PRIVATE);

			if (!appPath.exists()) {
				appPath.mkdirs();
			}

			try {
				//installScripts(assets, appPath, NODEJS_PATH);
				installPackage(assets, appPath);
			} catch (IOException e) {
				Log.e(TAG, "Error while installing script", e);
				return null;
			}

			String mainJS = params[0];
			File js = new File(appPath, NODEJS_PATH + "/" + mainJS);

			return js.toString();
		}

		@Override
		protected void onPostExecute(String js) {
			if (js != null) {
				Log.d(TAG, "run :" + js);
				NodeJSCore.run(js);
				Log.d(TAG, "run end");
			} else {
				NodeJSService.this.stopSelf();
			}
		}

	}

	public static void installScripts(AssetManager assets, File targetDir,
			String basePath) throws IOException {

		String[] files = assets.list(basePath);

		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}

		if (files.length == 0) {
			// basePath is a file. Copy file
			Log.d(TAG, "copy file: " + basePath);
			File targetFile = new File(targetDir, basePath);
			InputStream src = assets.open(basePath, AssetManager.ACCESS_BUFFER);
			File path = targetFile.getParentFile();

			if (!path.exists()) {
				path.mkdirs();
			}

			FileOutputStream out = new FileOutputStream(targetFile);
			try {
				byte[] buf = new byte[4096];
				int len;
	
				while ((len = src.read(buf)) > -1) {
					out.write(buf, 0, len);
				}
				
				out.flush();
			} finally {
				src.close();
				out.close();
			}

			return;
		} else {
			for (String file : files) {
				installScripts(assets, targetDir, basePath + "/" + file);
			}
		}
	}
	
	public static void installPackage(AssetManager assets, File targetDir) throws IOException {
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		
		ZipInputStream zin = new ZipInputStream(assets.open(DEFAULT_PACKAGE));
		
		ZipEntry ze = null;
		
		try {
			while((ze = zin.getNextEntry()) != null) {
				if(ze.isDirectory()) {
					File path = new File(targetDir, ze.getName());
					
					path.mkdirs();
				} else {
					File path = new File(targetDir, ze.getName());
					FileOutputStream out = new FileOutputStream(path);
					
					Log.d(TAG, "extract " + ze.getName() + " to " + path);
					
					byte[] buf = new byte[4096];
					int len;
					
					while((len = zin.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
					
					out.flush();
					out.close();
				}
			}
		} finally {		
			zin.close();
		}
	}

	public void runScript(String mainJS) throws IOException {
		NodeJSTask task = new NodeJSTask();
		task.execute(mainJS);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		PackageManager pm = getPackageManager();
		ComponentName component = new ComponentName(this, this.getClass());
		ServiceInfo info = null;

		Log.d(TAG, component.toString());

		try {
			info = pm.getServiceInfo(component, PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return START_NOT_STICKY;
		}

		Bundle metaData = info.metaData;

		String scriptName = metaData.getString("script");

		if (scriptName == null) {
			Log.e(TAG, "Script <" + scriptName
					+ "> is not set as service's meta");
			return START_NOT_STICKY;
		}

		try {
			runScript(scriptName);
		} catch (IOException e) {
			e.printStackTrace();
			return START_NOT_STICKY;
		}

		return START_STICKY;
	}

}
