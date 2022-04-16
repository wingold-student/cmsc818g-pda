package com.cmsc818g.StressContextEngine.Reporters;

import akka.actor.typed.ActorRef;

public interface BloodPressureReporter {
    interface Command {}

    static enum Passivate implements Command {
        INSTANCE
    }




    
}


