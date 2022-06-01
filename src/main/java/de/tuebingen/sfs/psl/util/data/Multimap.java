/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private Map<T1, Collection<T2>> map;
    private CollectionType collectionType;

    public Multimap(CollectionType collectionType) {
        map = new HashMap<>();
        this.collectionType = collectionType;
    }

    public boolean containsKey(T1 key) {
        return map.containsKey(key);
    }

    public boolean containsValue(T1 key, T2 value) {
        return containsKey(key) && get(key).contains(value);
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

    public Collection<T2> getOrDefault(T1 key, Collection<T2> defaultValue) {
        Collection<T2> value = get(key);
        if (value == null)
            return defaultValue;
        return value;
    }

    public List<T2> getList(T1 key) {
        if (collectionType.equals(CollectionType.LIST)) {
            return (List<T2>) map.get(key);
        }
        throw new RuntimeException("Multimap.getList only works when the collection type is set to LIST");
    }
    
    public List<T2> getListOrDefault(T1 key, List<T2> def) {
        if (collectionType.equals(CollectionType.LIST)) {
            return (List<T2>) map.getOrDefault(key, def);
        }
        throw new RuntimeException("Multimap.getList only works when the collection type is set to LIST");
    }

    public Set<T2> getSet(T1 key) {
        if (collectionType.equals(CollectionType.SET)) {
            return (Set<T2>) map.get(key);
        }
        throw new RuntimeException("Multimap.getSet only works when the collection type is set to SET");
    }

    public Set<T2> getSetOrDefault(T1 key, Set<T2> def) {
        if (collectionType.equals(CollectionType.SET)) {
            return (Set<T2>) map.getOrDefault(key, def);
        }
        throw new RuntimeException("Multimap.getSet only works when the collection type is set to SET");
    }
    
    public Set<T1> keySet() {
        return map.keySet();
    }

    public Collection<Collection<T2>> values() {
        return map.values();
    }

    public Set<Entry<T1, Collection<T2>>> entrySet() {
        return map.entrySet();
    }
    
    public Collection<T2> removeKey(T1 key) {
    	return map.remove(key);
    }

    public void removeFromOrDeleteCollection(T1 key, T2 value) {
        Collection<T2> values = map.get(key);
        if (values == null || values.isEmpty()) {
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
    
    public boolean isEmpty() {
    	return map.isEmpty();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Multimap[");
        String keyDelim = "";
        for (T1 key : map.keySet()) {
            sb.append(keyDelim).append(key).append("=");
            keyDelim = ", ";
            if (collectionType.equals(CollectionType.SET)) {
                sb.append("{");
            } else {
                sb.append("[");
            }
            String valDelim = "";
            for (T2 val : map.get(key)) {
                sb.append(valDelim).append(val);
                valDelim = ", ";
            }
            if (collectionType.equals(CollectionType.SET)) {
                sb.append("}");
            } else {
                sb.append("]");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public enum CollectionType {
        LIST, SET
    }

}
