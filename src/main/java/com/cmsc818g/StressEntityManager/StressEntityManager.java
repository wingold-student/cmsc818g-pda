package com.cmsc818g.StressEntityManager;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressEntityManager extends AbstractBehavior<StressEntityManager.Command> {
    
    public interface Command {}
    public static class entityManagerGreet implements Command {
        public final ActorRef<entityManagerResponse_controller> replyTo;

        public entityManagerGreet(ActorRef<entityManagerResponse_controller> ref) {
          this.replyTo = ref;
        }
      }//end of class entityManagerGreet
      public static final class entityManagerResponse_controller {
        public final String message;
        public final ArrayList<String> entityList;

        public entityManagerResponse_controller(String message, ArrayList<String> list) {
          this.entityList = list;
          this.message = message;
        }
      }//end of class entityManagerResponse_controller


    public static Behavior<Command> create() {
      System.out.println("entity");;
      return Behaviors.setup(context -> new StressEntityManager(context));
    }

    public StressEntityManager(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("Entity Manager actor created");
        //TODO: spawn entities
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(entityManagerGreet.class, this::onEngineResponse)
      .build();
    }
  
    private Behavior<Command> onEngineResponse(entityManagerGreet message) { //when receive message
        //get information of connected entities
        getContext().getLog().info("Get entity lists and send msg back");
        ArrayList<String> entities = new ArrayList<String>();
        entities.add("SmartWatch");
        entities.add("BloodPressureCuff");
        entities.add("Phone");
        entities.add("WorkCalender");
        entities.add("SchoolCalender");

        message.replyTo.tell(new entityManagerResponse_controller("entityManager", entities)); 
      return this;
    }



}
