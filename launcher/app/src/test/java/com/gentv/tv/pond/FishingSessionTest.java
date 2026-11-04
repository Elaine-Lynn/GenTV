package com.gentv.tv.pond;

import org.junit.Test;
public final class FishingSessionTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Test
 public void simulationRegression() {
  int caught=0;
  for(int seed=0;seed<400;seed++){
   PondWorld w=new PondWorld(seed);w.setCount(1);
   PondWorld.Fish fish=w.fish.get(0);
   FishingSession f=new FishingSession(seed);
   check(f.start()&&!f.start(),"Only wake once");check(f.sessionSeconds>=300&&f.sessionSeconds<=900,"Session bounds");
   fish.x=f.bobberX;fish.y=f.bobberY+32;
   for(int i=0;i<100;i++)f.update(.2f,w);
   check(f.stage==FishingSession.Stage.WAITING&&f.result==null,"Fish already present at casting must enter anew");
   fish.x=f.bobberX+70;f.update(.2f,w);
   for(int i=0;i<650;i++)f.update(.2f,w);
   check(f.stage==FishingSession.Stage.WAITING&&f.result==null,"Time alone must never trigger a catch");
   fish.x=f.bobberX;fish.y=f.bobberY;f.update(.2f,w);
   check(f.stage==FishingSession.Stage.WAITING,"The float itself is above the bite zone");
   fish.x=f.bobberX+25;fish.y=f.bobberY+32;f.update(.2f,w);
   check(f.stage==FishingSession.Stage.WAITING,"25 units from center is outside the reduced radius");
   fish.x=f.bobberX+23;f.update(.2f,w);
   if(f.stage==FishingSession.Stage.REELING){
    caught++;
    check(f.result.skin==fish.skin&&w.fish.size()==1&&!w.fish.contains(fish),"Catch removes the correct fish and preserves population");
    PondWorld.Fish replacement=w.fish.get(0);
    float clearance=PondWorld.waterClearance(replacement.x,replacement.y);
    check(clearance>=86&&clearance<95,"Replacement spawns near a wet bank");
    check((800-replacement.x)*Math.cos(replacement.angle)+(450-replacement.y)*Math.sin(replacement.angle)>0,"Replacement faces into the pond");
    f.elapsedSeconds=f.sessionSeconds;
    for(int i=0;i<20;i++)f.update(.2f,w);
    check(f.stage==FishingSession.Stage.RESULT,"An active catch finishes even at the session deadline");
    f.finish();
   }else{
    for(int i=0;i<100;i++)f.update(.2f,w);
    check(f.stage==FishingSession.Stage.WAITING&&w.fish.get(0)==fish,"A failed bite must not reroll each frame");
   }
   f.elapsedSeconds=f.sessionSeconds;f.update(.2f,w);
   check(f.stage==FishingSession.Stage.DOZING&&f.result==null,"Deadline returns to sleep");
  }
  check(caught>=165&&caught<=235,"Approximately half of entries should hook a fish, got "+caught);
 }
}
