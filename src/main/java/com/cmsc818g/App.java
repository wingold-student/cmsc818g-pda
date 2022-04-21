package com.cmsc818g;
import java.sql.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
    public static void main( String[] args )
    {
        final ActorSystem<StressManagementController.Command> system = ActorSystem.create(StressManagementController.create(), "pda-system");

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        system.terminate();

        }
   
        /*

        Connection connection = null;
        Statement statement = null;
        ResultSet set = null;
        try{  
            Class.forName("org.sqlite.JDBC");  
            connection = DriverManager.getConnection("jdbc:sqlite:DemoScenario.db");  
            statement = connection.createStatement();   
            //statement.executeUpdate("CREATE DATABASE STUDENTS");
            set = statement.executeQuery("select * from ScenarioForDemo");  
            while(set.next()){
                System.out.println("ID: "+ set.getString("id")
                                    + "     DateTime: " + set.getString("DateTime")
                                    + "     Event: " + set.getString("Event")
                                    + "     Location: " + set.getString("Location")
                                    + "     Schedule: " + set.getString("Schedule")
                                    + "      Heartbeat: " + set.getInt("Heartbeat")
                                    + "     Blood pressure: " + set.getInt("Bloodpressure")
                                    + "     Sleep hours: " + set.getInt("Sleephours"));
            }
     
            }catch(SQLException se){
                System.out.println("Connection Failed!");
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch blocks
                e.printStackTrace();
            }finally{
                try {
                if(connection != null)
                    connection.close();
                System.out.println("Connection closed !!");
                } catch (SQLException e) {
                e.printStackTrace();
                }
            }


        */
    
  
}