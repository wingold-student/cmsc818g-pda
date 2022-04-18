package com.cmsc818g.StressRecommendationEngine;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
/*
message from controller:
    current stress level
    past stress level
*/

public class StressRecommendationEngine extends AbstractBehavior<StressRecommendationEngine.Command> {

    public interface Command {}
    public static class recommendEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final StressManagementController.HealthInformation healthInfo; 
        public final ArrayList<String> list;
        
        public recommendEngineGreet(ActorRef<StressManagementController.Command> replyTo,
        StressManagementController.HealthInformation info, ArrayList<String> list) {
          this.replyTo = replyTo;
          this.healthInfo = info;
          this.list = list;
        }
      }//end of class recommendEngineGreet

    public static Behavior<Command> create() {
      return Behaviors.setup(context -> new StressRecommendationEngine(context));
    }

    public StressRecommendationEngine(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("Recommendation Engine actor created"); 
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(recommendEngineGreet.class, this::onEngineResponse)
      .build();
    }
  
    private Behavior<Command> onEngineResponse(recommendEngineGreet message) { //when receive message
        getContext().getLog().info("Get entity lists and send msg back");
        //recommend treatment     

        message.replyTo.tell(new StressManagementController.RecommendEngineToController("recommendation")); 
      return this;
    }


}
