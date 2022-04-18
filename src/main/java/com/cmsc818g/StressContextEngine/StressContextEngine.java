package com.cmsc818g.StressContextEngine;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}

    public static class contextEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final ArrayList<String> list;
        public final StressManagementController.HealthInformation healthInfo; 
        
        public contextEngineGreet(ActorRef<StressManagementController.Command> ref,
                      StressManagementController.HealthInformation info, ArrayList<String> list) {
          this.replyTo = ref;
          this.healthInfo = info;
          this.list = list;
        }
      }//end of class contextEngineGreet
 
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
        StressManagementController.HealthInformation personalData = new StressManagementController.HealthInformation();
   
        message.replyTo.tell(new StressManagementController.ContextEngineToController("healthData", personalData));       
      return this;
    }
}
