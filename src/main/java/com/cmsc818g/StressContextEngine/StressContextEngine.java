package com.cmsc818g.StressContextEngine;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public StressContextEngine(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }
}
