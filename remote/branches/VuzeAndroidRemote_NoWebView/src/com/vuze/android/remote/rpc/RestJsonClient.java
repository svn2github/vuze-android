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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
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
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import android.net.http.AndroidHttpClient;
import android.util.Log;

import com.aelitis.azureus.util.JSONUtils;
import com.vuze.android.remote.AndroidUtils;

public class RestJsonClient
{
	public static Object connect(String url)
			throws RPCException {
		return connect(url, null, null, null);
	}

	public static Map connect(String url, Map<?, ?> jsonPost, Header[] headers,
			UsernamePasswordCredentials creds)
			throws RPCException {
		if (AndroidUtils.DEBUG) {
			Log.d(null, "Execute " + url);
		}
		long now = System.currentTimeMillis();

		Map json = Collections.EMPTY_MAP;

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
					Log.d(null, "  Post: " + postString);
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
			if (AndroidUtils.DEBUG) {
				Log.d(null, "  conn ->" + (then - now) + "ms");
			}
			
			now = then;

			HttpEntity entity = response.getEntity();
			
			// XXX STATUSCODE!
			
			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				InputStreamReader isr = new InputStreamReader(instream, "utf8");
				BufferedReader br = new BufferedReader(isr);
				br.mark(32767);

				then = System.currentTimeMillis();
				if (AndroidUtils.DEBUG) {
					Log.d(null, "  readin ->" + (then - now) + "ms");
				}
				now = then;

				try {
					Object o = JSONValue.parseWithException(br);
					if (o instanceof Map) {
						json = (Map) o;
					} else {
  					// could be : ArrayList, String, Number, Boolean
  					Map map = new HashMap();
  					map.put("value", o);
  					json = map;
					}

				} catch (ParseException pe) {

					br.reset();
					String line = br.readLine().trim();

					if (AndroidUtils.DEBUG) {
						Log.d(null, "line: " + line);
					}
					Header contentType = entity.getContentType();
					if (line.startsWith("<")
							|| line.contains("<html")
							|| (contentType != null && contentType.getValue().startsWith(
									"text/html"))) {
						// TODO: use android strings.xml
						throw new RPCException(response,
								"Could not retrieve remote client location information.  The most common cause is being on a guest wifi that requires login before using the internet.");
					}

					throw new RPCException(pe);
				} finally {
					br.close();
				}

				if (AndroidUtils.DEBUG) {
					Log.d(null, "JSON Result: " + json);
				}

			}
		} catch (RPCException e) {
			throw e;
		} catch (Throwable e) {
			throw new RPCException(e);
		}

		if (AndroidUtils.DEBUG) {
			long then = System.currentTimeMillis();
			Log.d(null, "  parse ->" + (then - now) + "ms");
		}
		return json;
	}

}
