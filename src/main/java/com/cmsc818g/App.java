package com.cmsc818g;

import java.io.IOException;

import akka.actor.typed.ActorSystem;

/**
 * Just something to start up the system and keep it running.
 * It will keep running until the user inputs something into the terminal.
 * 
 * We could potentially have a terminal interface along with the web interface,
 * maybe just for testing purposes?
 */
public class App 
{
    public static void main( String[] args ) throws Exception  {
        final ActorSystem<StressManagementController.Command> system = ActorSystem.create(StressManagementController.create("configs/controller.yml"), "pda-system");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

       system.terminate();
     }
    
}