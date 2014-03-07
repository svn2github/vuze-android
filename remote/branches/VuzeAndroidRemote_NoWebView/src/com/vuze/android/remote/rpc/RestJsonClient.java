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

package com.vuze.android.remote.rpc;

import java.io.*;
import java.util.Collections;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.util.Log;

import com.aelitis.azureus.util.JSONUtils;
import com.vuze.android.remote.AndroidUtils;

/**
 * Connects to URL, decodes JSON results
 * 
 */
public class RestJsonClient
{
	private static final String TAG = "RPC";
	
	private static final boolean DEBUG_DETAILED = false;

	public static Object connect(String url)
			throws RPCException {
		return connect("", url, null, null, null);
	}

	public static Map<?, ?> connect(String id, String url, Map<?, ?> jsonPost, Header[] headers,
			UsernamePasswordCredentials creds)
			throws RPCException {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, id + "] Execute " + url);
		}
		long now = System.currentTimeMillis();

		Map<?, ?> json = Collections.EMPTY_MAP;

		try {

			BasicHttpParams basicHttpParams = new BasicHttpParams();
			HttpProtocolParams.setUserAgent(basicHttpParams, "Vuze Android Remote");
			//AndroidHttpClient.newInstance("Vuze Android Remote");
			DefaultHttpClient httpclient = new DefaultHttpClient(basicHttpParams);
			httpclient.getCredentialsProvider().setCredentials(
					new AuthScope(null, -1), creds);

			// Prepare a request object
			HttpRequestBase httpRequest = jsonPost == null ? new HttpGet(url)
					: new HttpPost(url); // IllegalArgumentException

			if (jsonPost != null) {
				HttpPost post = (HttpPost) httpRequest;
				String postString = JSONUtils.encodeToJSON(jsonPost);
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, id + "]  Post: " + postString);
				}
				post.setEntity(new StringEntity(postString));
				post.setHeader("Accept", "application/json");
				post.setHeader("Content-type",
						"application/x-www-form-urlencoded; charset=UTF-8");
			}

			if (headers != null) {
				for (Header header : headers) {
					httpRequest.setHeader(header);
				}
			}

			// Execute the request
			HttpResponse response;

			response = httpclient.execute(httpRequest);

			long then = System.currentTimeMillis();
			if (DEBUG_DETAILED) {
  			if (AndroidUtils.DEBUG) {
  				Log.d(TAG, id + "]  conn ->" + (then - now) + "ms");
  			}
  			now = then;
			}

			HttpEntity entity = response.getEntity();

			// XXX STATUSCODE!

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				InputStreamReader isr = new InputStreamReader(instream, "utf8");
				StringBuilder sb = new StringBuilder();
				//BufferedReader br = new BufferedReader(isr);
				//br.mark(32767);

				try {
					
  				char c[] = new char[8192];
  				while (true) {
  						int read = isr.read(c);
  						if (read < 0) {
  							break;
  						}
  						sb.append(c, 0, read);
  				}
  				

  				if (DEBUG_DETAILED) {
  					then = System.currentTimeMillis();
  					if (AndroidUtils.DEBUG) {
  						Log.d(TAG, id + "] " + sb.toString());
  						Log.d(TAG, id + "]  read ->" + (then - now) + "ms");
  					}
  					now = then;
  				}

  				
					// 9775 files
					// 33xx-3800 for simple; 22xx for GSON 2.2.4; 18xx-19xx for fastjson 1.1.34
//					json = JSONUtils.decodeJSON(br);
  				json = JSONUtils.decodeJSON(sb.toString());


				} catch (Exception pe) {

					try {

//  					br.reset();
//  					String line = br.readLine().trim();
						isr.close();
						String line = sb.subSequence(0, Math.min(128, sb.length())).toString();
  
  					if (AndroidUtils.DEBUG) {
  						Log.d(TAG, id + "]line: " + line);
  					}
  					Header contentType = entity.getContentType();
  					if (line.startsWith("<")
  							|| line.contains("<html")
  							|| (contentType != null && contentType.getValue().startsWith(
  									"text/html"))) {
  						// TODO: use android strings.xml
  						throw new RPCException(
  								response,
  								"Could not retrieve remote client location information.  The most common cause is being on a guest wifi that requires login before using the internet.");
  					}
					} catch (IOException io) {
						
					}

					throw new RPCException(pe);
				} finally {
					isr.close();
//					br.close();
				}

				if (AndroidUtils.DEBUG) {
//					Log.d(TAG, id + "]JSON Result: " + json);
				}

			}
		} catch (RPCException e) {
			Log.e(TAG, id, e);
			throw e;
		} catch (Throwable e) {
			Log.e(TAG, id, e);
			throw new RPCException(e);
		}

		if (AndroidUtils.DEBUG) {
			long then = System.currentTimeMillis();
			Log.d(TAG, id + "]  " + (DEBUG_DETAILED ? "parse" : "received in")  +" ->" + (then - now) + "ms");
		}
		return json;
	}

}
