package com.hxzhitang.tongdarailway.railway.fixed;

import java.util.*;
import net.minecraft.core.*;

/** Bounded terrain-guided corridors; heights come from noise columns, never loaded chunks. */
public final class NaturalRailwayLayout {
    @FunctionalInterface public interface Heights { int get(int x,int z); }
    private NaturalRailwayLayout() {}
    private static final int[] OFFSETS={-1200,-800,-400,0,400,800,1200};
    public static FixedRailwayNetwork.Network create(long seed,RegionalTopology.Unit unit,List<BlockPos> critical,Heights heights,int sea) {
        var random=new Random(RegionalTopology.key(seed,unit.x(),unit.z(),0x4e41545552414cL));
        double angle=random.nextDouble()*Math.PI,dx=Math.cos(angle),dz=Math.sin(angle),nx=-dz,nz=dx;
        int cx=unit.x()*4096+6144+random.nextInt(1801)-900,cz=unit.z()*4096+6144+random.nextInt(1801)-900;
        var landmarks=critical.stream().filter(p->unit.cells().contains(RegionalTopology.region(p))).toList();
        if(!landmarks.isEmpty()) {
            // Shift the corridor sideways toward a required destination, without centering the whole line on it.
            var site=landmarks.getFirst().offset(192,0,192);double shift=(site.getX()-cx)*nx+(site.getZ()-cz)*nz;
            cx+=(int)(nx*shift);cz+=(int)(nz*shift);
        }
        int count=4+random.nextInt(2);double length=8600+random.nextInt(1401),phase=random.nextDouble()*Math.PI*2;
        var candidates=new ArrayList<List<BlockPos>>();
        for(int i=0;i<count;i++) {
            double along=-length/2+i*length/(count-1),bend=600*Math.sin(phase+i*.9);
            var row=new ArrayList<BlockPos>();
            for(int side:OFFSETS)row.add(unit.clamp(new BlockPos((int)(cx+along*dx+(bend+side)*nx),80,(int)(cz+along*dz+(bend+side)*nz)),640));
            candidates.add(row);
        }
        var terrain=new Terrain(heights,sea);var spine=choose(candidates,terrain);
        var nodes=new ArrayList<FixedRailwayNetwork.Node>();
        for(int i=0;i<spine.size();i++) {
            var pos=spine.get(i);
            if(nodes.stream().anyMatch(n->n.rail().distSqr(pos)<500L*500))continue;
            nodes.add(new FixedRailwayNetwork.Node("spine_"+i,pos,Direction.WEST,pos,0,"corridor_stop",i==0?"small_wood_station_2":"eow_small_halt",0));
        }
        // A loop is a return branch between two existing spine stops, never a pre-arranged polygon.
        if(nodes.size()>=4&&random.nextDouble()<.4) {
            var a=nodes.get(1);var b=nodes.get(nodes.size()-2);double bx=b.rail().getX()-a.rail().getX(),bz=b.rail().getZ()-a.rail().getZ();
            double distance=Math.max(1,Math.hypot(bx,bz)),sx=-bz/distance,sz=bx/distance;
            double width=1500+random.nextInt(701);List<BlockPos> best=null;double bestCost=Double.POSITIVE_INFINITY;
            for(int sign:new int[]{-1,1}) {
                var points=new ArrayList<BlockPos>();
                for(int i=1;i<=2;i++) {
                    double t=i/3.0,offset=width*(i==1?1:.78)*sign;
                    var expected=unit.clamp(new BlockPos((int)(a.rail().getX()+bx*t+sx*offset),80,(int)(a.rail().getZ()+bz*t+sz*offset)),640);
                    points.add(expected);
                }
                double score=terrain.edge(a.rail(),points.getFirst())+terrain.edge(points.getFirst(),points.getLast())+terrain.edge(points.getLast(),b.rail());
                if(score<bestCost){bestCost=score;best=points;}
            }
            for(int i=0;i<2;i++) {
                var pos=best.get(i);var anchor=i==0?a:b;
                if(nodes.stream().anyMatch(n->n.rail().distSqr(pos)<650L*650))continue;
                nodes.add(new FixedRailwayNetwork.Node("return_"+i,pos,Direction.WEST,pos,0,"return_to_"+anchor.id(),"eow_small_halt",0));
            }
        }
        for(var site:landmarks)if(nodes.size()<8) {
            var required=AutomaticRailwayNetwork.candidates(seed,List.of(site)).nodes().stream().filter(n->n.role().equals("critical")).findFirst().orElseThrow();
            nodes.add(new FixedRailwayNetwork.Node("critical_"+nodes.size(),unit.clamp(required.rail(),256),required.facing(),site,required.radius(),"critical",required.station(),required.rotation()));
        }
        return new FixedRailwayNetwork.Network(seed,nodes,RegionalRailwayNetwork.longLinks(nodes));
    }
    /** Seven lateral options per station, with bounded dynamic programming instead of a world-wide search. */
    private static List<BlockPos> choose(List<List<BlockPos>> rows,Terrain terrain) {
        int n=rows.size(),m=OFFSETS.length;double[][] cost=new double[n][m];int[][] previous=new int[n][m];
        for(int j=0;j<m;j++)cost[0][j]=terrain.site(rows.getFirst().get(j))+Math.abs(OFFSETS[j])*.03;
        for(int i=1;i<n;i++)for(int j=0;j<m;j++) {
            cost[i][j]=Double.POSITIVE_INFINITY;
            for(int k=0;k<m;k++) {
                var a=rows.get(i-1).get(k);var b=rows.get(i).get(j);double turn=0;
                if(i>1){var p=rows.get(i-2).get(previous[i-1][k]);double ux=a.getX()-p.getX(),uz=a.getZ()-p.getZ(),vx=b.getX()-a.getX(),vz=b.getZ()-a.getZ();turn=220*(1-(ux*vx+uz*vz)/Math.max(1,Math.hypot(ux,uz)*Math.hypot(vx,vz)));}
                double score=cost[i-1][k]+terrain.edge(a,b)+terrain.site(b)+turn+Math.abs(OFFSETS[j])*.025;
                if(score<cost[i][j]){cost[i][j]=score;previous[i][j]=k;}
            }
        }
        int last=0;for(int j=1;j<m;j++)if(cost[n-1][j]<cost[n-1][last])last=j;
        var result=new ArrayList<BlockPos>(Collections.nCopies(n,BlockPos.ZERO));
        for(int i=n-1;i>=0;i--){result.set(i,rows.get(i).get(last));last=previous[i][last];}return result;
    }
    private static final class Terrain {
        final Heights source;final int sea;final Map<Long,Integer> cache=new HashMap<>();
        Terrain(Heights source,int sea){this.source=source;this.sea=sea;}
        int height(int x,int z){x=Math.floorDiv(x+128,256)*256;z=Math.floorDiv(z+128,256)*256;long key=((long)x<<32)^(z&0xffffffffL);final int px=x,pz=z;return cache.computeIfAbsent(key,k->source.get(px,pz));}
        double site(BlockPos p){int h=height(p.getX(),p.getZ());return Math.max(0,sea+2-h)*15+Math.abs(height(p.getX()+256,p.getZ())-h)*2+Math.abs(height(p.getX(),p.getZ()+256)-h)*2;}
        double edge(BlockPos a,BlockPos b) {
            double length=Math.sqrt(a.distSqr(b));if(length>3900)return 100000+length;
            double cost=length*.04;int prior=height(a.getX(),a.getZ());
            // Four coarse columns per transition cap work independently of route length.
            for(int i=1;i<=4;i++){int h=height(a.getX()+(b.getX()-a.getX())*i/4,a.getZ()+(b.getZ()-a.getZ())*i/4);cost+=Math.abs(h-prior)*3+Math.max(0,sea+2-h)*10;prior=h;}return cost;
        }
    }
}
