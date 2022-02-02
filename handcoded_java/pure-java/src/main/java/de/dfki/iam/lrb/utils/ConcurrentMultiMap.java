package de.dfki.iam.lrb.utils;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ConcurrentMultiMap<Key, Value> {

    private final ConcurrentHashMap<Key, ConcurrentSkipListSet<Value>> containers;
    private final ConcurrentSkipListSet<Value> emptySet;

    public ConcurrentMultiMap() {
        this.containers = new ConcurrentHashMap<>();
        this.emptySet = new ConcurrentSkipListSet<>();
    }

    public Iterator<Value> get(Key key) {
        return containers.get(key).iterator();
    }

    public Set<Value> getSet(Key key) {
        return containers.get(key);
    }

    public boolean containsKey(Key key) {
        return containers.containsKey(key);
    }


    public int count(Key key) {
        if (containers.containsKey(key)) {
            return containers.get(key).size();
        }
        return 0;
    }

    public boolean put(Key key, Value value) {
        boolean[] result = new boolean[] { false };
        while (putHelper(key, value, result)) { }
        return result[0];
    }

    private boolean putHelper(Key key, Value value, boolean[] out) {

        boolean retry = false;
        boolean added = false;

        ConcurrentSkipListSet<Value> set = containers.get(key);

        if (set != null) {
            synchronized (set) {
                if (set.isEmpty()) {
                    retry = true;
                } else {
                    added = set.add(value);
                    retry = false;
                }
            }
        } else {
            ConcurrentSkipListSet<Value> newSet = new ConcurrentSkipListSet<>();
            newSet.add(value);
            ConcurrentSkipListSet<Value> oldSet = containers.putIfAbsent(key, newSet);
            if (oldSet != null) {
                synchronized (oldSet) {
                    if (oldSet.isEmpty()) {
                        retry = true;
                    } else {
                        added = oldSet.add(value);
                        retry= false;
                    }
                }
            } else {
                added = true;
            }
        }
        out[0] = added;
        return retry;
    }

    public Iterable<Value> remove(Key key) {
        ConcurrentSkipListSet<Value> set = containers.get(key);
        if (set != null) {
            synchronized (set) {
                containers.remove(key, set);
                ConcurrentSkipListSet<Value> ret = set.clone();
                set.clear();
                return ret;
            }
        }
        return null;
    }


    public boolean remove(Key key, Value value) {
        ConcurrentSkipListSet<Value> set = containers.get(key);
        if (set != null) {
            synchronized (set) {
                if (set.remove(value)) {
                    if (set.isEmpty()) {
                        containers.remove(key, emptySet);
                        return true;
                    }
                }
            }
        }
        return false;
    }



}
