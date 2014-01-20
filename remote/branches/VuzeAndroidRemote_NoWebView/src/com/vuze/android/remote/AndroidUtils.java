/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package com.vuze.android.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.dialog.DialogFragmentMoveData;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

public class AndroidUtils
{
	public static final boolean DEBUG = true;

	private static boolean hasAlertDialogOpen = false;

	private static boolean webViewsPaused;

	public static class AlertDialogBuilder
	{
		public View view;

		public AlertDialog.Builder builder;

		public AlertDialogBuilder(View view, Builder builder) {
			super();
			this.view = view;
			this.builder = builder;
		}

	}

	/**
	 * Creates an AlertDialog.Builder that has the proper theme for Gingerbread
	 */
	public static AndroidUtils.AlertDialogBuilder createAlertDialogBuilder(
			Activity activity, int resource) {
		AlertDialog.Builder builder;
		Context c;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			// ContextThemeWrapper needed for <= v10 because there is no AlertDialog.Builder(Context, theme)
			c = new ContextThemeWrapper(activity, android.R.style.Theme_Dialog);
			builder = new AlertDialog.Builder(c);
		} else {
			builder = new AlertDialog.Builder(activity);
			c = activity;
		}

		View view = View.inflate(c, resource, null);
		builder.setView(view);

		return new AndroidUtils.AlertDialogBuilder(view, builder);
	}

	public static void openSingleAlertDialog(AlertDialog.Builder builder) {
		openSingleAlertDialog(builder, null);
	}

	public static void openSingleAlertDialog(AlertDialog.Builder builder,
			final OnDismissListener dismissListener) {

		// We should always be on the UI Thread, so no need to synchronize
		if (hasAlertDialogOpen) {
			return;
		}

		hasAlertDialogOpen = true;
		AlertDialog show = builder.show();
		// Note: There's a builder.setOnDismissListener(), but it's API 17
		show.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				hasAlertDialogOpen = false;
				if (dismissListener != null) {
					dismissListener.onDismiss(dialog);
				}
			}
		});
	}

	public static void showConnectionError(Activity activity, int errMsgID,
			boolean allowContinue) {
		String errMsg = activity.getResources().getString(errMsgID);
		showConnectionError(activity, errMsg, allowContinue);
	}

	public static void showConnectionError(final Activity activity,
			final String errMsg, final boolean allowContinue) {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				Builder builder = new AlertDialog.Builder(activity).setTitle(
						R.string.error_connecting).setMessage(errMsg).setCancelable(true).setNegativeButton(
						R.string.action_logout, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								if (activity.isTaskRoot()) {
									new RemoteUtils(activity).openRemoteList(activity.getIntent());
								}
								activity.finish();
							}
						});
				if (allowContinue) {
					builder.setPositiveButton(R.string.button_continue,
							new OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
								}
							});
				}
				AndroidUtils.openSingleAlertDialog(builder);
			}
		});

	}

	public static void showDialog(Activity activity, int titleID, String msg) {
		String title = activity.getResources().getString(titleID);
		showDialog(activity, title, msg);
	}

	public static void showDialog(final Activity activity, final String title,
			final String msg) {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				Builder builder = new AlertDialog.Builder(activity).setMessage(msg).setCancelable(
						true).setNegativeButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								if (activity.isTaskRoot()) {
									new RemoteUtils(activity).openRemoteList(activity.getIntent());
								}
								activity.finish();
							}
						});
				if (title != null) {
					builder.setTitle(title);
				}
				builder.show();
			}
		});

	}

	public static void showFeatureRequiresVuze(final Activity activity,
			final String feature) {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					if (DEBUG) {
						System.out.println("can't display -- finishing");
					}
					return;
				}
				String msg = activity.getResources().getString(R.string.vuze_required,
						feature);
				Builder builder = new AlertDialog.Builder(activity).setMessage(msg).setCancelable(
						true).setPositiveButton(android.R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				builder.show();
			}
		});

	}

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connManager == null) {
			return false;
		}
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (mWifi == null) {
			return false;
		}

		return mWifi.isConnected();
	}

	public static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		return false;
	}

	// ACTION_POWER_CONNECTED
	public static boolean isConnected(Context context) {
		Intent intent = context.registerReceiver(null, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));
		int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		return plugged == BatteryManager.BATTERY_PLUGGED_AC
				|| plugged == BatteryManager.BATTERY_PLUGGED_USB;
	}

	// From http://
	public static void openFileChooser(Activity activity, String mimeType,
			int requestCode) {

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(mimeType);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		// special intent for Samsung file manager
		Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");

		sIntent.putExtra("CONTENT_TYPE", mimeType);
		sIntent.addCategory(Intent.CATEGORY_DEFAULT);

		Intent chooserIntent;
		if (activity.getPackageManager().resolveActivity(sIntent, 0) != null) {
			chooserIntent = Intent.createChooser(sIntent, "Open file");
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {
				intent
			});
		} else {
			chooserIntent = Intent.createChooser(intent, "Open file");
		}

		if (chooserIntent != null) {
			try {
				activity.startActivityForResult(chooserIntent, requestCode);
				return;
			} catch (android.content.ActivityNotFoundException ex) {
			}
		}
		Toast.makeText(activity.getApplicationContext(),
				activity.getResources().getString(R.string.no_file_chooser),
				Toast.LENGTH_SHORT).show();
	}

	public static boolean areWebViewsPaused() {
		return webViewsPaused;
	}

	public static void setWebViewsPaused(boolean paused) {
		webViewsPaused = paused;
	}

	public static void handleConsoleMessageFroyo(Context ctx, String message,
			String sourceId, int lineNumber, String page) {
		Log.d("console.log", message + " -- line " + lineNumber + " of " + sourceId);
		if (message != null && message.startsWith("Uncaught")) {
			if (sourceId == null) {
				sourceId = "unknown";
			}
			if (sourceId.indexOf('/') > 0) {
				sourceId = sourceId.substring(sourceId.lastIndexOf('/'));
			}
			int qPos = sourceId.indexOf('?');
			if (qPos > 0 && sourceId.length() > 1) {
				sourceId = sourceId.substring(0, qPos);
			}
			String s = sourceId + ":" + lineNumber + " " + message.substring(9);
			if (s.length() > 100) {
				s = s.substring(0, 100);
			}
			VuzeEasyTracker.getInstance(ctx).logError(ctx, s, page);
		}
	}

	public static void linkify(View view, int widgetId, int textId) {
		TextView textview = (TextView) view.findViewById(widgetId);
		if (textview != null) {
			textview.setMovementMethod(LinkMovementMethod.getInstance());
			textview.setText(Html.fromHtml(view.getResources().getString(textId)));
		}
	}

	public static void linkify(Activity view, int widgetId, int textId) {
		TextView textview = (TextView) view.findViewById(widgetId);
		if (textview != null) {
			textview.setMovementMethod(LinkMovementMethod.getInstance());
			textview.setText(Html.fromHtml(view.getResources().getString(textId)));
		}
	}

	/**
	 * Remove all extras from intent
	 */
	public static void clearExtras(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras == null) {
			return;
		}
		for (String key : extras.keySet()) {
			intent.removeExtra(key);
		}
	}

	/**
	 * Android doesn't fade out disbaled menu item icons, so do it ourselves
	 */
	public static void fixupMenuAlpha(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			Drawable icon = item.getIcon();
			if (icon != null) {
				icon.setAlpha(item.isEnabled() ? 255 : 64);
			}
		}
	}

	public static class ValueStringArray
	{
		public int values[];

		public String strings[];

		public ValueStringArray(int[] value, String[] string) {
			this.values = value;
			this.strings = string;
		}

	}

	public static ValueStringArray getValueStringArray(Resources resources, int id) {
		String[] stringArray = resources.getStringArray(id);
		String[] strings = new String[stringArray.length];
		int[] values = new int[stringArray.length];
		
		for (int i = 0; i < stringArray.length; i++) {
			String[] s = stringArray[i].split(",");
			values[i] = Integer.parseInt(s[0]);
			strings[i] = s[1];
		}
		return new ValueStringArray(values, strings);
	}

	@SuppressWarnings("rawtypes")
	public static void openMoveDataDialog(Map mapTorrent, SessionInfo sessionInfo, FragmentManager fm) {
		DialogFragmentMoveData dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		if (mapTorrent == null) {
			return;
		}

		bundle.putLong("id", MapUtils.getMapLong(mapTorrent, "id", -1));
		bundle.putString("name", "" + mapTorrent.get("name"));

		String defaultDownloadDir = sessionInfo.getSessionSettings().getDownloadDir();
		String downloadDir = MapUtils.getMapString(mapTorrent, "downloadDir",
				defaultDownloadDir);
		bundle.putString("downloadDir", downloadDir);
		ArrayList<String> history = new ArrayList<String>();
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		List<String> saveHistory = sessionInfo.getRemoteProfile().getSavePathHistory();
		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList("history", history);
		dlg.setArguments(bundle);
		dlg.show(fm, "MoveDataDialog");
	}

}
