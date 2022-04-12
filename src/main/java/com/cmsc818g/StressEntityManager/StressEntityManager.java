package com.cmsc818g.StressEntityManager;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressEntityManager extends AbstractBehavior<StressEntityManager.Command> {
    
    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }

    public StressEntityManager(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }

}
