package com.cmsc818g.StressEntityManager.Entities;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class CalendarEntity extends AbstractBehavior<CalendarCommand> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/

    /************************************* 
     * CREATION 
     *************************************/

    private String calendarAPIURL;

    public CalendarEntity(ActorContext<CalendarCommand> context, String calendarAPIURL) {
        super(context);
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<CalendarCommand> createReceive() {
        return null;
    }
    
}
