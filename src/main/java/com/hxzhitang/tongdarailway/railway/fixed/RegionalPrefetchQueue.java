package com.hxzhitang.tongdarailway.railway.fixed;
import java.util.*;
/** A bounded, replaceable queue drops stale neighborhoods when players move. */
public final class RegionalPrefetchQueue<K,V> {
    private final int limit;private final LinkedHashMap<K,V> pending=new LinkedHashMap<>();
    public RegionalPrefetchQueue(int limit){this.limit=limit;}
    public synchronized void replace(Map<K,V> wanted){pending.clear();for(var e:wanted.entrySet()){if(pending.size()>=limit)break;pending.putIfAbsent(e.getKey(),e.getValue());}}
    public synchronized V poll(){var it=pending.entrySet().iterator();if(!it.hasNext())return null;var item=it.next();it.remove();return item.getValue();}
    public synchronized int size(){return pending.size();}
    public synchronized void clear(){pending.clear();}
}
