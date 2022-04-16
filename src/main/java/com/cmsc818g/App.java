package com.cmsc818g;

import java.io.IOException;

import akka.actor.typed.ActorSystem;
public class App 
{
    public static void main( String[] args )
    {
        ActorSystem<Void> system = ActorSystem.create(PdaSupervisor.create(), "pda-system");

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        system.terminate();
    }
}
