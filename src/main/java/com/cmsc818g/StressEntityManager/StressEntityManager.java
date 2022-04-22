package com.cmsc818g.StressEntityManager;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressEntityManager.Entities.CalendarCommand;
import com.cmsc818g.StressEntityManager.Entities.CalendarEntity;
import com.cmsc818g.StressEntityManager.Entities.PhoneEntity;
import com.cmsc818g.StressEntityManager.Entities.SmartWatchDevice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressEntityManager extends AbstractBehavior<StressEntityManager.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}
    public static class entityManagerGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;

        public entityManagerGreet(ActorRef<StressManagementController.Command> replyTo) {
          this.replyTo = replyTo;
        }
    }//end of class entityManagerGreet

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/

    
    /************************************* 
     * CREATION 
     *************************************/
    private ActorRef<PhoneEntity.Command> phoneEntity;
    private ActorRef<CalendarCommand> calendarEntity;
    private ActorRef<SmartWatchDevice.Command> smartWatchEntity;

    private final String phoneName = "PhoneEntity";
    private final String calendarName = "CalendarEntity";
    private final String smartWatchName = "SmartWatchEntity";
    private ArrayList<String> entities;

    public static Behavior<Command> create(String databaseURI, String tableName) {
      return Behaviors.setup(context -> new StressEntityManager(context, databaseURI, tableName));
    }

    public StressEntityManager(ActorContext<Command> context, String databaseURI, String tableName) {
        super(context);
        getContext().getLog().info("Entity Manager actor created");

        phoneEntity = context.spawn(PhoneEntity.create(context.getSelf(), databaseURI, tableName), phoneName);
        calendarEntity = context.spawn(CalendarEntity.create(databaseURI, tableName), calendarName);
        smartWatchEntity = context.spawn(SmartWatchDevice.create(databaseURI, tableName), smartWatchName);

        entities = new ArrayList<String>();

        entities.add(smartWatchName);
        entities.add(phoneName);
        entities.add(calendarName);
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder().onMessage(entityManagerGreet.class, this::onEngineResponse)
      .build();
    }
  
    private Behavior<Command> onEngineResponse(entityManagerGreet message) { //when receive message
      //get information of connected entities
      getContext().getLog().info("Get entity lists and send msg back");
      message.replyTo.tell(new StressManagementController.EntityManagerToController("entityList", entities)); 
      return this;
    }



}
