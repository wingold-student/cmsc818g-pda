package com.cmsc818g.StressContextEngine;

import java.util.ArrayList;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}

    public static class contextEngineGreet implements Command {
        public final ActorRef<contextEngineResponse_controller> replyTo;
        public contextEngineGreet(ActorRef<contextEngineResponse_controller> ref) {
          this.replyTo = ref;
        }
      }//end of class contextEngineGreet
      public static final class contextEngineResponse_controller {
        public final String message;
        public final ArrayList<String> entityList;

        public contextEngineResponse_controller(String message, ArrayList<String> list) {
          this.entityList = list;
          this.message = message;
        }
      }//end of class contextEngineResponse
      public static class HealthInformation {
        public final int bloodPressure = 98;
        public final int heartRate = 65;
        public final int sleepLevel = 0;
        public final String location = "home";
        public final int BusynessLevel = 0;
        public final int stressLevel = 0;
        //scheduler
        //medical history
      }
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressContextEngine(context));
    }
/*
    public static Behavior<Command> create(ActorRef<StressManagementController.Command> controller) {
      return Behaviors.setup(context -> new StressContextEngine(context, controller));
    }
*/

    public StressContextEngine(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("context engine actor created");
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(contextEngineGreet.class, this::onEngineResponse).build();
    }
  
    private Behavior<Command> onEngineResponse(contextEngineGreet message) { //when receive message
        //get information of connected entities
        ArrayList<String> entities = new ArrayList<String>();
   
        message.replyTo.tell(new contextEngineResponse_controller("contextEngine", entities));       
      return this;
    }
}
