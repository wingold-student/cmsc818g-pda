package com.cmsc818g;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressManagementController extends AbstractBehavior<StressManagementController.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    } 

   public StressManagementController(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }
}
