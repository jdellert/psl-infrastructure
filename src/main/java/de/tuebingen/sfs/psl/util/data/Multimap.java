package de.tuebingen.sfs.psl.util.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Multimap<T1, T2> {

	public enum CollectionType {
		LIST, SET
	}

	private Map<T1, Collection<T2>> map;
	private CollectionType collectionType;

	public Multimap(CollectionType collectionType) {
		map = new HashMap<>();
		this.collectionType = collectionType;
	}

	public void put(T1 key, T2 value) {
		Collection<T2> values = map.get(key);
		if (values == null) {
			switch (collectionType) {
			case LIST:
				values = new ArrayList<>();
				break;
			case SET:
				values = new HashSet<>();
			}
			values.add(value);
			map.put(key, values);
			return;
		}
		values.add(value);
	}

	public void putAll(T1 key, Collection<T2> values) {
		Collection<T2> existingValues = map.get(key);
		if (existingValues == null || existingValues.isEmpty()) {
			if ((collectionType.equals(CollectionType.SET) && values instanceof Set)
					|| (collectionType.equals(CollectionType.LIST) && values instanceof List)) {
				map.put(key, values);
				return;
			}
			switch (collectionType) {
			case LIST:
				existingValues = new ArrayList<>();
				break;
			case SET:
				existingValues = new HashSet<>();
			}
		}
		existingValues.addAll(values);
	}

	public Collection<T2> get(T1 key) {
		return map.get(key);
	}

	public List<T2> getList(T1 key) {
		if (collectionType.equals(CollectionType.LIST)) {
			return (List<T2>) map.get(key);
		}
		throw new RuntimeException("Multimap.getList only works when the collection type is set to LIST");
	}

	public Set<T2> getSet(T1 key) {
		if (collectionType.equals(CollectionType.SET)) {
			return (Set<T2>) map.get(key);
		}
		throw new RuntimeException("Multimap.getSet only works when the collection type is set to SET");
	}

	public Set<T1> keySet() {
		return map.keySet();
	}
	
	public Collection<Collection<T2>> values() {
		return map.values();
	}
	
	public Set<Entry<T1, Collection<T2>>> entrySet(){
		return map.entrySet();
	}

	public void removeFromOrDeleteCollection(T1 key, T2 value) {
		Collection<T2> values = map.get(key);
		if (values == null || values.isEmpty()){
			System.err.println(
					"Tried to remove " + value + " from the collection for " + key + ", but no such key existed.");
			map.remove(key);
			return;
		}
		values.remove(value);
		if (values.isEmpty()) {
			map.remove(key);
		}
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("Multimap[");
		String keyDelim = "";
		for (T1 key : map.keySet()){
			sb.append(keyDelim).append(key).append("=");
			keyDelim = ", ";
			if (collectionType.equals(CollectionType.SET)){
				sb.append("{");
			} else {
				sb.append("[");
			}
			String valDelim = "";
			for (T2 val : map.get(key)){
				sb.append(valDelim).append(val);
				valDelim = ", ";
			}
			if (collectionType.equals(CollectionType.SET)){
				sb.append("}");
			} else {
				sb.append("]");
			}
		}
		sb.append("]");
		return sb.toString();
	}

}
