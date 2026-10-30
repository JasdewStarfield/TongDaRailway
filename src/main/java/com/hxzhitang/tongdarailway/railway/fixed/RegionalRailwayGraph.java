package com.hxzhitang.tongdarailway.railway.fixed;

import java.util.*;

/** Bounded topology from frozen POI candidates; never visits or generates world chunks. */
public final class RegionalRailwayGraph {
    private RegionalRailwayGraph() {}
    private record Edge(FixedRailwayNetwork.Node a,FixedRailwayNetwork.Node b,double cost) {}
    public static List<FixedRailwayNetwork.Link> connect(List<FixedRailwayNetwork.Node> candidates) {
        return connect(candidates,true);
    }
    /** Automatic optional regions can retain disconnected critical stations without rejecting the whole world. */
    public static List<FixedRailwayNetwork.Link> connectAvailable(List<FixedRailwayNetwork.Node> candidates) {
        return connect(candidates,false);
    }
    private static List<FixedRailwayNetwork.Link> connect(List<FixedRailwayNetwork.Node> candidates,boolean required) {
        var nodes=candidates.stream().sorted(Comparator.comparing(FixedRailwayNetwork.Node::id)).toList();
        var edges=new ArrayList<Edge>();
        for(int i=0;i<nodes.size();i++)for(int j=i+1;j<nodes.size();j++) {
            var a=nodes.get(i);var b=nodes.get(j);double cost=a.rail().distSqr(b.rail());
            if(cost<=4096L*4096)edges.add(new Edge(a,b,cost));
        }
        edges.sort(Comparator.comparingDouble(Edge::cost).thenComparing(e->e.a().id()).thenComparing(e->e.b().id()));
        var groups=new HashMap<String,String>();var degree=new HashMap<String,Integer>();
        nodes.forEach(n->{groups.put(n.id(),n.id());degree.put(n.id(),0);});
        var result=new ArrayList<FixedRailwayNetwork.Link>();
        for(var edge:edges) {
            String a=edge.a().id(),b=edge.b().id(),ga=groups.get(a),gb=groups.get(b);
            if(ga.equals(gb)||degree.get(a)>=3||degree.get(b)>=3)continue;
            result.add(new FixedRailwayNetwork.Link(a,b));degree.merge(a,1,Integer::sum);degree.merge(b,1,Integer::sum);
            groups.replaceAll((key,value)->value.equals(gb)?ga:value);
        }
        if(required&&!nodes.isEmpty()&&result.size()!=nodes.size()-1)throw new IllegalArgumentException("Candidates cannot form a bounded connected railway network");
        return List.copyOf(result);
    }
}
