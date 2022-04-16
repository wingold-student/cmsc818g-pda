package com.cmsc818g.StressContextEngine;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public StressContextEngine(ActorContext<Command> context) {
        super(context);
        System.out.println("[My] context engine actor created");
    }

    public static Behavior<StressContextEngine.Command> create() {
        return Behaviors.setup(StressContextEngine::new);
    }
    public interface Command {}
 
    public static class engineGreet implements Command {
        public final ActorRef<engineResponse> respondTo;
        public engineGreet(ActorRef<engineResponse> ref) {
          this.respondTo = ref;
        }
      }//end of class engineCommunication
  
      public static final class engineResponse {
        public final String message;
        public engineResponse(String message) {
          this.message = message;
        }
      }//end of class engineResponse

/*
    public static Behavior<Command> create(ActorRef<StressManagementController.Command> controller) {
      return Behaviors.setup(context -> new StressContextEngine(context, controller));
    }
*/
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(engineGreet.class, this::engineGreet).build();
    }
  
    private Behavior<Command> engineGreet(engineGreet message) { //when receive message
        message.respondTo.tell(new engineResponse("I'm sorry. I'm afraid I can't do that."));
        System.out.println("message: " + message);
       
      return this;
    }
}
