package com.gentv.tv.pond;

import org.junit.Test;

public final class PondWorldTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void advance(PondWorld w,int frames){for(int i=0;i<frames;i++)w.update(1/60f);}
    private static void finite(PondWorld w){for(PondWorld.Fish f:w.fish){check(Float.isFinite(f.x)&&Float.isFinite(f.y)&&Float.isFinite(f.angle)&&Float.isFinite(f.turn)&&Float.isFinite(f.depth),"Non-finite fish state");check(f.x>=20&&f.x<=1580&&f.y>=20&&f.y<=880,"Fish out of bounds");check(PondWorld.waterClearance(f.x,f.y)>=55,"Fish must stay clear of all four banks");}}
    @Test
    public void simulationRegression() {
        for(int hz:new int[]{15,20,30,60}){PondWorld clock=new PondWorld(99);clock.setCount(0);clock.feed(800,450);for(int i=0;i<hz*36;i++)clock.update(1f/hz);check(Math.abs(clock.time-36)<.02f,"Real-time simulation at "+hz+"fps");check(clock.food.isEmpty(),"Food expiry at "+hz+"fps");}
        PondWorld splash=new PondWorld(1);splash.setCount(0);splash.feed(800,450);
        advance(splash,12);check(splash.feedImpacts==0&&splash.ripples.isEmpty(),"No impact before pellets land");
        advance(splash,18);check(splash.feedImpacts==1&&splash.ripples.size()==1,"A handful makes one audible impact and wake");
        for(PondWorld.Food p:splash.food)check(p.age>0,"All pellets have landed");
        check(PondWorld.waterClearance(820,53)<0&&PondWorld.waterClearance(1504,277)<0,"Top and right lily pads occlude");
        check(PondWorld.waterClearance(145,638)<0&&PondWorld.waterClearance(1465,638)<0,"Left and right rock shelves occlude");
        check(PondWorld.waterClearance(800,450)>150,"Submerged gravel remains open water");
        PondWorld ambient=new PondWorld(101);ambient.setCount(0);
        PondWorld.Leaf firstLeaf=null;boolean fell=false,landed=false,left=false,drop=false;
        float landedX=0,landedY=0,lastClearance=0;
        for(int i=0;i<60*300&&!left;i++){
            float previousAge=firstLeaf==null?-1:firstLeaf.age;
            ambient.update(1/60f);
            for(PondWorld.Ripple r:ambient.ripples){
                if(r.power==.54f){drop=true;check(PondWorld.waterClearance(r.x,r.y)>65,"Drop ripple starts on water");}
            }
            if(firstLeaf==null&&!ambient.leaves.isEmpty())firstLeaf=ambient.leaves.get(0);
            if(firstLeaf==null)continue;
            if(firstLeaf.age<0){
                fell=true;
                for(PondWorld.Ripple r:ambient.ripples)check(r.power!=.48f,"No leaf ripple while falling");
            }else if(previousAge<0){
                landed=true;landedX=firstLeaf.x;landedY=firstLeaf.y;
                check(ambient.ripples.stream().filter(r->r.power==.48f).count()==1,"Leaf landing makes one ripple");
            }
            if(landed&&!ambient.leaves.contains(firstLeaf)){
                check(lastClearance<2,"Leaf disappears at the water boundary");
                check(Math.hypot(firstLeaf.x-landedX,firstLeaf.y-landedY)>30,"Leaf drifts after landing");
                left=true;
            }
            lastClearance=PondWorld.waterClearance(firstLeaf.x,firstLeaf.y);
        }
        check(drop&&fell&&landed&&left,"Ambient drop and full leaf lifecycle");
        PondWorld shore=new PondWorld(217);check(shore.feed(130,720),"Feeding near fisherman accepted");
        for(PondWorld.Food pellet:shore.food)check(pellet.x>PondWorld.shoreX(pellet.y)+25,"Food must fall in water, not dry bank");
        for(int i=0;i<60*180;i++){shore.update(1/60f);finite(shore);check(shore.turtle.x>=PondWorld.shoreX(shore.turtle.y)+54,"Turtle must stay off dry terrace");}
        PondWorld corners=new PondWorld(68431);corners.setCount(40);
        float[] lastX=new float[40],lastY=new float[40];int[] blocked=new int[40];
        for(int frame=0;frame<60*300;frame++){
            corners.update(1/60f);
            for(int i=0;i<40;i++){
                PondWorld.Fish fish=corners.fish.get(i);
                blocked[i]=fish.x==lastX[i]&&fish.y==lastY[i]?blocked[i]+1:0;
                check(blocked[i]<60*5,"Fish must turn out of a blocked shoreline corner");
                lastX[i]=fish.x;lastY[i]=fish.y;
            }
        }
        PondWorld lowRate=new PondWorld(123),normalRate=new PondWorld(123);lowRate.feed(800,450);normalRate.feed(800,450);
        for(int i=0;i<150;i++){lowRate.update(1/15f);normalRate.update(1/30f);normalRate.update(1/30f);}
        for(int i=0;i<20;i++)check(Math.hypot(lowRate.fish.get(i).x-normalRate.fish.get(i).x,lowRate.fish.get(i).y-normalRate.fish.get(i).y)<.01,"15fps preserves swimming trajectory");
        check(Math.hypot(lowRate.turtle.x-normalRate.turtle.x,lowRate.turtle.y-normalRate.turtle.y)<.01,"15fps preserves turtle trajectory");
        check(lowRate.eaten==normalRate.eaten,"Low frame rate preserves feeding");
        PondWorld stalled=new PondWorld(99);stalled.update(10);check(stalled.time<=.201f,"Bounded catch-up after stall");
        PondWorld w=new PondWorld(68431);check(w.fish.size()==20,"Initial population");float x=w.fish.get(0).x,y=w.fish.get(0).y;advance(w,600);check(Math.hypot(x-w.fish.get(0).x,y-w.fish.get(0).y)>30,"Fish must roam");finite(w);
        PondWorld.Fish first=w.fish.get(0);check(w.feed(first.x+60,first.y),"First feeding accepted");check(w.food.size()==12,"Feed quantity");check(!w.feed(800,450),"Feeding debounce");advance(w,120);boolean attracted=false;for(PondWorld.Fish f:w.fish)attracted|=f.target!=null;check(attracted,"Fish respond to food");advance(w,1500);check(w.eaten>0,"Fish eat instead of endlessly orbiting");
        first=w.fish.get(0);w.disturb(first.x,first.y);check(first.fear>0,"Ripple fright response");float fear=first.fear;advance(w,60);check(first.fear<fear,"Fear must decay");
        w.setCount(40);check(w.fish.size()==40,"Population increase");for(int i=0;i<120;i++){advance(w,45);w.feed(800,450);check(w.food.size()<=96,"Food cap");check(w.ripples.size()<=12,"Ripple cap");finite(w);}
        advance(w,18000);finite(w);check(w.food.isEmpty(),"Food expires after 35s");w.setCount(0);advance(w,300);check(w.ripples.isEmpty(),"Ripples expire");w.setCount(99);check(w.fish.size()==40,"Population upper bound");w.setCount(-1);check(w.fish.isEmpty(),"Population lower bound");
        w.cursorX=800;w.cursorY=450;w.moveCursor(100000,-100000);check(w.cursorX==1530&&w.cursorY==70,"Cursor bounds");
        w.time=628.2f;check(w.feed(800,450),"Feed before time rollover");advance(w,15);check(w.time<1,"Animation time must roll over");check(!w.feed(800,450),"Rollover must preserve feed cooldown");advance(w,60);check(w.feed(800,450),"Feed after rollover cooldown");
    }
}
