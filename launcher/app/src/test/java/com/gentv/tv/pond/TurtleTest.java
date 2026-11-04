package com.gentv.tv.pond;

import org.junit.Test;

public final class TurtleTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void advance(PondWorld w,int frames){for(int i=0;i<frames;i++)w.update(1/60f);}
    private static double distance(float[] a,int one,int two){return Math.hypot((a[one]-a[two])*800,(a[one+1]-a[two+1])*450);}
    @Test
    public void turtleCarriesMomentumBetweenStrokes() {
        PondWorld world = new PondWorld(134);
        PondWorld.Turtle turtle = world.turtle;
        turtle.paddling = false;
        turtle.motionTime = 2f;
        turtle.speed = 16f;
        float x = turtle.x;
        float y = turtle.y;
        advance(world, 90);
        check(!turtle.paddling, "Turtle is still gliding");
        check(Math.hypot(turtle.x - x, turtle.y - y) > 14, "Glide carries the turtle across the bed");
        check(turtle.speed > 7.5f && turtle.speed < 16f, "Glide retains momentum while slowing");
    }

    @Test
    public void simulationRegression() {
        PondWorld w=new PondWorld(134);w.setCount(0);PondWorld.Turtle t=w.turtle;
        check(t!=null&&t.pose!=null,"Exactly one persistent turtle");float startX=t.x,startY=t.y;advance(w,600);check(Math.hypot(t.x-startX,t.y-startY)>20,"Turtle must swim");
        check(w.feed(t.x+(float)Math.cos(t.angle)*25,t.y+(float)Math.sin(t.angle)*25),"Place nearby turtle food");advance(w,1200);check(t.eaten>0,"Turtle must approach and eat nearby food");
        w.disturb(t.x-15,t.y);check(t.fear>0,"Turtle must respond to ripples");advance(w,240);check(t.fear==0,"Turtle must calm down");
        int shellA=(13*KoiPose.ROWS+3)*4,shellB=(21*KoiPose.ROWS+5)*4,foot=(9*KoiPose.ROWS)*4;
        t.phase=0;t.pose.update(t);float[] before=t.pose.vertices.clone();double shellLength=distance(before,shellA,shellB);
        t.phase=(float)Math.PI/2;t.pose.update(t);float[] after=t.pose.vertices;
        check(Math.abs(distance(after,shellA,shellB)-shellLength)<.001,"Carapace must remain rigid");
        check(Math.hypot((after[foot]-before[foot])*800,(after[foot+1]-before[foot+1])*450)>1,"Foot must paddle independently");
        for(int i=0;i<60*180;i++){w.update(1/60f);check(t.x>=35&&t.x<=1565&&t.y>=35&&t.y<=865,"Turtle stays in pond");for(float v:t.pose.vertices)check(Float.isFinite(v),"Finite turtle mesh");}
        w.setCount(40);check(w.turtle==t,"Fish count must not add or replace turtle");check(t.speed<20,"Turtle keeps its slower movement rhythm");
    }
}
