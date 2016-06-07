package com.atc.simulator.PredictionService.Engine;

import com.atc.simulator.Config.ApplicationConfig;
import com.atc.simulator.PredictionService.PredictionFeedServerThread;
import com.atc.simulator.PredictionService.SystemStateDatabase;
import com.atc.simulator.PredictionService.SystemStateDatabaseListener;
import com.atc.simulator.RunnableThread;
import com.atc.simulator.flightdata.AircraftState;
import com.atc.simulator.flightdata.Prediction;
import com.atc.simulator.flightdata.Track;
import com.atc.simulator.vectors.GeographicCoordinate;

import java.util.ArrayList;

/**
* Basic engine, will receive system states, break them into AircraftStates, create predictions and push to the server
*
* @
* PUBLIC FEATURES:
* // Constructors
*    Engine(PredictionFeedServerThread)
* // Methods
*    onSystemUpdate() - Listener Override, adds new AircraftStates to the Buffer
*    run() - Thread of checking buffer and passing messages to server
*    kill() - Clears the flag that the client thread runs off, letting the thread finish gracefully
*    start() - Start new Thread
*    makeNewPrediction(AircraftState) - Create a new prediction for the AircraftState given
*
* MODIFIED:
* @version 0.1, CC 29/05/16, Initial creation/beginning of a general framework
* @author    Chris Coleman, 7191375
* TODO: implement the PredictionEngineListener functionality
*/
public class PredictionEngineThread implements RunnableThread, SystemStateDatabaseListener{
    private PredictionEngineTodoQueue todoQueue; //TODO: make this threadsafe and possibly use a seperate buffer
    private SystemStateDatabase systemStateDatabase;
    private ArrayList<PredictionWorkerThread> workerPool;


    //Internal settings
    private PredictionFeedServerThread predictionFeedServer;
    //Thread definitions
    private boolean continueThread = true;  //Simple flag that dictates whether the Server threads will keep looping
    private Thread thread;
    private final String threadName = "PredictionEngineThread";


    /**
     * Constructor, connect to the server and create a new arrayList
     * @param predictionFeedServer : The server I need to connect to
     */
    public PredictionEngineThread(PredictionFeedServerThread predictionFeedServer, SystemStateDatabase systemStateDatabase)
    {
        this.predictionFeedServer = predictionFeedServer;
        todoQueue = new PredictionEngineTodoQueue();
        workerPool = new ArrayList<PredictionWorkerThread>();
        workerPool.add(new PredictionWorkerThread(0, this));
        workerPool.add(new PredictionWorkerThread(1, this));
//        workerPool.add(new PredictionWorkerThread(2, this));
        this.systemStateDatabase = systemStateDatabase;
    }

    /**
     * Waits for a new work item to be available, and when available
     * marks it as started, and then returns it to the worker who
     * requested it.
     * @param worker worker who is requesting this work item
     * @return
     */
    public PredictionWorkItem startWorkItem(PredictionWorkerThread worker)
    {
        //there is a potential here for orphan threads to be created that never get killed...
        PredictionWorkItem workItem = todoQueue.take();
        workItem.startWorking(worker);
        return  workItem;
    }

    /**
     * Tell the PredictionEngineThread that a work item has been
     * completed by one of its workers.
     * @param workItem
     */
    public void completeWorkItem(PredictionWorkItem workItem)
    {
        predictionFeedServer.sendPrediction(workItem.getPrediction());
    }

    /**
     * currently this does nothing, in the future it could be used
     * to reprioritize items in the queue.
     */
    public void run() {
        while (continueThread)
        {
            if(todoQueue.peek() != null) {
//                makeNewPrediction(todoQueue.poll());
            }
            try { //Then sleep for a bit
                Thread.sleep(100);
            } catch (InterruptedException i){}
        }
    }

    /**
     * Small method called too kill the server's threads when the have run through
     */
    public void kill()
    {
        continueThread = false;
        for (PredictionWorkerThread worker: workerPool)
        {
            worker.kill();
        }
    }

     /**
      * Join this thread.
      */
     @Override
     public void join() throws InterruptedException {
         for (PredictionWorkerThread worker: workerPool)
         {
             worker.join();
         }
         thread.join();
     }

     /**
     * Start this thread
     */
    public void start()
    {
        if (thread == null)
        {
            thread = new Thread(this, threadName);
            thread.start();
        }

        for (PredictionWorkerThread worker: workerPool)
        {
            worker.start();
        }
    }

     /**
      * Interface implementation for SystemStateDatabaseListener
      * gets called whenever the SystemStateDatabase receives an update.
      * @param aircraftIDs
      */
     @Override
     public void onSystemStateUpdate(ArrayList<String> aircraftIDs) {
         for (String aircraftID: aircraftIDs)
         {
             Track aircraftTrack = systemStateDatabase.getTrack(aircraftID); //TODO: make this a deep copy
             PredictionWorkItem workItem = new PredictionWorkItem(
                     aircraftID,
                     aircraftTrack,
                     PredictionAlgorithmType.PASSTHROUGH);
             //TODO: make a seperate buffer for this so it doesn't block while todoQueue is being reordered?
             ApplicationConfig.debugPrint(
                     "print-queues",
                     threadName + " Adding to queue which has a current size of " + todoQueue.size());
             todoQueue.add(workItem); //TODO: make this an addinorder call
         }
     }
 }