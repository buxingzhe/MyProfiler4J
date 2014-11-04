package org.myprofiler4j.java.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.myprofiler4j.java.json.JsonArray;
import org.myprofiler4j.java.json.JsonObject;
import org.myprofiler4j.java.json.JsonValue;
import org.myprofiler4j.java.json.annotation.JSONField;


public final class JsonUtil {
	private final static Object[] NULL_ARGS = new Object[0];
	private final static Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<String, Class<?>>();
	
	static {
		PRIMITIVE_TYPES.put(byte[].class.getName(), byte[].class);
		PRIMITIVE_TYPES.put(short[].class.getName(), short[].class);
		PRIMITIVE_TYPES.put(int[].class.getName(), int[].class);
		PRIMITIVE_TYPES.put(float[].class.getName(), float[].class);
		PRIMITIVE_TYPES.put(long[].class.getName(), long[].class);
		PRIMITIVE_TYPES.put(double[].class.getName(), double[].class);
		PRIMITIVE_TYPES.put(boolean[].class.getName(), boolean[].class);
		PRIMITIVE_TYPES.put(char[].class.getName(), char[].class);
		
		PRIMITIVE_TYPES.put("byte", byte.class);
		PRIMITIVE_TYPES.put("short", short.class);
		PRIMITIVE_TYPES.put("int", int.class);
		PRIMITIVE_TYPES.put("float", float.class);
		PRIMITIVE_TYPES.put("long", long.class);
		PRIMITIVE_TYPES.put("double", double.class);
		PRIMITIVE_TYPES.put("boolean", boolean.class);
		PRIMITIVE_TYPES.put("char", char.class);
	}
	
	@SuppressWarnings("unchecked")
	public static JsonValue toJSonString(Object object) {
		Class<?> clz = null;
		if(object == null) {
			return JsonValue.NULL;
		} else {
			clz = object.getClass();
		}
		
		if(object instanceof String) {
			return JsonValue.valueOf((String)object);
		} else if(object instanceof Number) {
			return numberToJsonValue(clz, (Number)object);
		} else if(clz.isArray()) {
			return toJsonArray(object);
		} else if(object instanceof Collection<?>) {
			return collectionToJsonValue((Collection<?>)object);
		} else if(object instanceof Map<?, ?>) {
			return mapToJsonValue((Map<Object, Object>)object);
		} else if(object instanceof Boolean) {
			return JsonValue.valueOf(((Boolean)object).booleanValue());
		} else if(object instanceof Character) {
			return JsonValue.valueOf(String.valueOf(((Character)object).charValue()));
		}
		
		Method[] methods = clz.getMethods();
		JsonObject jo = new JsonObject();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			JSONField jf = method.getAnnotation(JSONField.class);
			if(jf == null) continue;
			if(jf != null) {
				String fieldKey = jf.name();
				if("".equals(fieldKey)) continue;
				try {
					Object fieldValue = method.invoke(object, NULL_ARGS);
					Class<?> fieldClz = null;
					if(fieldValue == null) {
						jo.add(fieldKey, JsonValue.NULL);
						continue;
					} else {
						fieldClz = fieldValue.getClass();
					}
					
					if(fieldValue instanceof Number) {
						jo.add(fieldKey, numberToJsonValue(fieldClz, (Number)fieldValue));
					} else if(fieldValue instanceof Boolean) {
						jo.add(fieldKey, JsonValue.valueOf(((Boolean)fieldValue).booleanValue()));
					} else if(fieldValue instanceof Character) {
						jo.add(fieldKey, String.valueOf(((Character)fieldValue).charValue()));
					} else if(fieldValue instanceof String){
						jo.add(fieldKey, (String)fieldValue);
					} else if(fieldValue instanceof Date) {
						String format = jf.format();
						if(!"".equals(format)) {
							try {
								DateFormat df = new SimpleDateFormat(format);
								jo.add(fieldKey, df.format((Date) fieldValue));
								continue;
							} catch (Exception e) {
							}
						}
						jo.add(fieldKey, JsonValue.readFrom(String.valueOf(fieldValue)));
					} else {
						jo.add(fieldKey, toJSonString(fieldValue));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return jo;
	}
	
	private static JsonValue numberToJsonValue(Class<?> clz, Number number) {
		if(clz == Byte.class) {
			return JsonValue.valueOf(((Number)number).byteValue());
		} else if(clz == Short.class) {
			return JsonValue.valueOf(((Number)number).shortValue());
		} else if(clz == Integer.class) {
			return JsonValue.valueOf(((Number)number).intValue());
		} else if(clz == Float.class) {
			return JsonValue.valueOf(((Number)number).floatValue());
		} else if(clz == Long.class) {
			return JsonValue.valueOf(((Number)number).longValue());
		} else if(clz == Double.class) {
			return JsonValue.valueOf(((Number)number).doubleValue());
		}
		return JsonValue.NULL;
	}
	
	private static JsonValue collectionToJsonValue(Collection<?> collection) {
		if(collection == null) return JsonValue.NULL;
		JsonArray arrayObj = new JsonArray();
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext();) {
			Object object = (Object) iterator.next();
			arrayObj.add(toJSonString(object));
		}
		return arrayObj;
	}
	
	private static JsonValue mapToJsonValue(Map<Object, Object> map) {
		if(map == null) return JsonValue.NULL;
		Set<Map.Entry<Object, Object>> set = map.entrySet();
		JsonArray result = new JsonArray();
		for (Iterator<Map.Entry<Object, Object>> iterator = set.iterator(); iterator.hasNext();) {
			Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) iterator.next();
			JsonObject o = new JsonObject();
			o.add("key", toJSonString(entry.getKey()));
			o.add("value", toJSonString(entry.getValue()));
			result.add(o);
		}
		return result;
	}
	
	public static JsonValue toJsonArray(Object arrayObject) {
		if(arrayObject == null) return JsonValue.NULL;
		Class<?> arrayClass = arrayObject.getClass();
		if(arrayClass.isArray()) {
			if(PRIMITIVE_TYPES.containsKey(arrayClass.getName())) {
				try {
					Method m = Arrays.class.getDeclaredMethod("toString", PRIMITIVE_TYPES.get(arrayClass.getName()));
					return JsonArray.readFrom((String)m.invoke(null, arrayObject));
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				int len = Array.getLength(arrayObject);
				JsonArray objArray = new JsonArray();
				for (int j = 0; j < len; j++) {
					Object obj = Array.get(arrayObject, j);
					objArray.add(toJSonString(obj));
				}
				return objArray;
			}
		}
		return JsonValue.NULL;
	}
	
	public static JsonObject parseObject(String text) {
		return JsonObject.readFrom(text);
	}
}
