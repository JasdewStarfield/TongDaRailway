package com.hxzhitang.tongdarailway.railway.fixed;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BiConsumer;

/** Coalesce unloaded graph frontiers and revisit them fairly when chunks become available. */
final class FrontierRetryQueue<K,A> {
    private final LinkedHashMap<K,LinkedHashSet<A>> entries=new LinkedHashMap<>();
    void add(K key,A anchor) {
        var anchors=entries.computeIfAbsent(key,k->new LinkedHashSet<>());
        if(anchors.size()<4)anchors.add(anchor);
        while(entries.size()>4096)entries.remove(entries.keySet().iterator().next());
    }
    void poll(int budget,BiPredicate<K,A> ready,BiConsumer<K,A> retry) {
        // Rotate blocked frontiers so one absent chunk cannot starve later ready neighbours.
        var keys=new ArrayList<>(entries.keySet()).stream().limit(budget).toList();
        for(var key:keys) {
            var anchors=entries.remove(key);if(anchors==null)continue;
            var it=anchors.iterator();
            while(it.hasNext()) {
                var anchor=it.next();
                if(ready.test(key,anchor)){it.remove();retry.accept(key,anchor);break;}
            }
            if(!anchors.isEmpty())entries.put(key,anchors);
        }
    }
    int size(){return entries.size();}
}
