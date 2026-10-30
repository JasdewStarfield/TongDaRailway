package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Assign all links of a station together; processing order must not choose its entrance. */
public final class StationPortAssignment {
    public record Assignment(List<Integer> ports,double cost) {}
    private StationPortAssignment() {}
    public static Assignment solve(List<Vec3> neighbours,List<Vec3> exits) {
        if(exits.isEmpty())throw new IllegalArgumentException("Station has no native exits");
        return search(neighbours,exits,new ArrayList<>(),0);
    }
    private static Assignment search(List<Vec3> neighbours,List<Vec3> exits,List<Integer> ports,double cost) {
        if(ports.size()==neighbours.size())return new Assignment(List.copyOf(ports),cost);
        int index=ports.size();Assignment best=null;
        var bearing=neighbours.get(index).multiply(1,0,1).normalize();
        for(int p=0;p<exits.size();p++) {
            double extra=4*(1-bearing.dot(exits.get(p).multiply(1,0,1).normalize()));
            for(int previous:ports)if(previous==p)extra+=4;
            ports.add(p);var candidate=search(neighbours,exits,ports,cost+extra);ports.removeLast();
            if(best==null||candidate.cost()<best.cost()-1e-8)best=candidate;
        }
        return best;
    }
}
