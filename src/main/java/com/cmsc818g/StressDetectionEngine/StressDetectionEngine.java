package com.cmsc818g.StressDetectionEngine;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
public class StressDetectionEngine extends AbstractBehavior<StressDetectionEngine.Command> {

    public interface Command {}
    public static class detectionEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final StressManagementController.HealthInformation healthInfo; //send health info to detection engine
        public final ArrayList<String> list;

        public detectionEngineGreet(ActorRef<StressManagementController.Command> ref, 
                StressManagementController.HealthInformation info, ArrayList<String> list) {
          this.replyTo = ref;
          this.healthInfo = info;
          this.list = list;
        }
      }//end of class detectionEngineGreet
 
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressDetectionEngine(context));
    }

    public StressDetectionEngine(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("context engine actor created");
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(detectionEngineGreet.class, this::onEngineResponse).build();
    }
  
    private Behavior<Command> onEngineResponse(detectionEngineGreet message) { //when receive message
        //get information of connected entities
        //StressManagementController.HealthInformation personalData = new StressManagementController.HealthInformation();
        int level = 100 ; //stress level
        message.replyTo.tell(new StressManagementController.DetectionEngineToController("stressLevel", level));       
      return this;
    }
}
