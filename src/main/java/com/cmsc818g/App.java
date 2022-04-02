package com.cmsc818g;

import akka.actor.typed.ActorSystem;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        ActorSystem.create(PdaSupervisor.create(), "pda-system");
    }
}
