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

package com.aelitis.azureus.util;

import java.io.Reader;
import java.util.*;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONReader;
import com.vuze.android.remote.VuzeEasyTracker;

/**
 * Wrap JSON functions so we can easily switch out JSON library.
 * <p>
 * On a torrent with 9775 files, pulling "files" and "fileStats":<BR>
 * 33xx-3800 for simple
 * 22xx for GSON 2.2.4
 * 18xx-19xx for fastjson 1.1.34 (stream Reader broken on chunks > 8192)
 */
@SuppressWarnings({
	"unchecked",
	"rawtypes"
})
public class JSONUtils
{

	/**
	 * decodes JSON formatted text into a map.
	 * 
	 * @return Map parsed from a JSON formatted string
	 * <p>
	 *  If the json text is not a map, a map with the key "value" will be returned.
	 *  the value of "value" will either be an List, String, Number, Boolean, or null
	 *  <p>
	 *  if the String is formatted badly, null is returned
	 */
	public static Map decodeJSONnoException(String json) {
		try {
			return decodeJSON(json);
		} catch (Exception e) {
			System.err.println("Parsing " + json);
			e.printStackTrace();
			return null;
		}
	}

	public static Map decodeJSONnoException(Context context, String json) {
		try {
			return decodeJSON(json);
		} catch (Exception e) {
			e.printStackTrace();
			VuzeEasyTracker.getInstance(context).logError(context, e);
			return null;
		}
	}

	
	public static Map decodeJSON(String json)
			throws Exception {
		Object object = parseWithException(json);
		//System.out.println("decode: " + json + "\nto\n" + object);
		if (object instanceof Map) {
			return (Map) object;
		}
		// could be : ArrayList, String, Number, Boolean
		Map map = new HashMap();
		map.put("value", object);
		return map;
	}

	public static Map decodeJSON(Reader json)
			throws Exception {
		Object object = parseWithException(json);
		//System.out.println("decode: " + json + "\nto\n" + object);
		if (object instanceof Map) {
			return (Map) object;
		}
		// could be : ArrayList, String, Number, Boolean
		Map map = new HashMap();
		map.put("value", object);
		return map;
	}

	private static Object parseWithException(String json) {
		return JSON.parse(json);
	}

	private static Object parseWithException(Reader reader) {
		return new JSONReader(reader).readObject();
	}

	public static List decodeJSONList(String json) {
		try {
			Object object = parseWithException(json);
			if (object instanceof List) {
				return (List) object;
			}
			// could be : Map, String, Number, Boolean
			List list = new ArrayList();
			list.add(object);
			return list;
		} catch (Throwable t) {
			return null;
		}
	}

	private static Map encodeMap(Map map) {
		for (Object key : map.keySet()) {
			Object value = map.get(key);
			if (value instanceof Map) {
				encodeMap((Map) value);
				continue;
			}
			if (value != null && value.getClass().isArray()) {
				value =  Arrays.asList((Object[]) value);
				map.put(key, value);
			}
		}
		return map;
	}

	public static String encodeToJSON(Map map) {
		return JSON.toJSONString(map);
	}

	public static String encodeToJSON(Collection list) {
		return JSON.toJSONString(list);
	}

	/*
	public static Map decodeJSON(Reader br) throws Exception {
		JSONReader jsonReader = new JSONReader(br);
		// Breaks on objects > 8192 bytes
		Object readObject = jsonReader.readObject(HashMap.class);
		return (Map) readObject;
	}
	*/
}
