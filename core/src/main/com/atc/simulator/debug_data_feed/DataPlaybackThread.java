package com.atc.simulator.debug_data_feed;

import com.atc.simulator.config.ApplicationConfig;
import com.atc.simulator.debug_data_feed.scenarios.Scenario;
import com.atc.simulator.RunnableThread;
import com.atc.simulator.flightdata.SystemState;
import com.atc.simulator.flightdata.TimeSource;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by luke on 24/05/16.
 *
 * @author Luke Frisken
 * @author Chris Coleman
 *
 */
public class DataPlaybackThread implements RunnableThread, TimeSource {
    private ArrayList<DataPlaybackListener> listeners;
    private int updateRate;
    private Scenario scenario;
    private final String threadName = "DataPlayback";
    private Thread thread;
    private long currentTime;
    private boolean continueThread;
    private boolean running;
    private static final int speed = ApplicationConfig.getInt("settings.debug-data-feed.speed");
    // for thread pausing
    private volatile boolean paused;
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();

    /**
     * Constructor for DataPlaybackThread
     * @param scenario the scenario to be played out by this thread
     * @param updateRate (in millisconds)
     */
    public DataPlaybackThread(Scenario scenario, int updateRate)
    {
        listeners = new ArrayList<DataPlaybackListener>();
        this.updateRate = updateRate;
        this.scenario = scenario;

        currentTime = scenario.getStartTime();
        continueThread = true;
        running = false;
        paused = false;

    }

    /**
     * Add a listener
     * @param listener
     */
    public void addListener(DataPlaybackListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     * @param listener
     */
    public void removeListener(DataPlaybackListener listener) {
        listeners.remove(listener);
    }

    /**
     * Trigger an onSystemState event on all the DataPlaybackListener listeners to this thread
     * @param systemState
     */
    private void triggerOnSystemUpdate(SystemState systemState)
    {
        for(DataPlaybackListener listener: listeners)
        {
            listener.onSystemUpdate(systemState);
        }
    }

    /**
     * The run method of this thread
     */
    @Override
    public void run() {
        running = true;
        long endTime = scenario.getEndTime();
        while(continueThread)
        {
            pauseLock.lock(); //Lock the unpausing to this Thread so others can't change it during this test
            if(paused)
            {
                try{
                    unpaused.await(); //Thread has been told to wait, so stop doing anything
                }catch (InterruptedException ex){break;}
            }
            pauseLock.unlock();

            try {
                //sleep for the desired update rate.
                //TODO: beware this is not precise tracking 1:1 of the time in the track
                //would be better to use the difference in the system clock, and interpolate
                //the values along the track in the Scenario. This is easier and better for performance for now.
                Thread.sleep(updateRate/speed);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            currentTime = getCurrentTime() + updateRate; //20 times speedup TODO: remove


            //finish if we have passed the end time
            if (getCurrentTime() > endTime)
            {
                return;
            }

//            System.out.println("Debug CurrentTime");
//            System.out.println("CurrentTime: " + ISO8601.fromCalendar(currentTime));

                SystemState state = scenario.getState(getCurrentTime());


//            System.out.println("StateTime: " + ISO8601.fromCalendar(state.getTime()));

            triggerOnSystemUpdate(state);
        }
        running = false;
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
    }

    /**
     * Kill this thread.
     */
    @Override
    public void kill() {
        continueThread = false;
    }

    /**
     * Join this thread.
     */
    @Override
    public void join() throws InterruptedException {
        thread.join();
    }

    /**
     * Set whether or not the playback thread is paused.
     * @param paused whether or not the playback thread is paused.
     */
    public void setPaused(boolean paused)
    {
        pauseLock.lock();
        this.paused = paused;
        if (!paused)
        {
            unpaused.signalAll();
        }
        pauseLock.unlock();
    }

    /**
     * Get whether or not this playback thread is paused
     */
    public boolean getPaused()
    {
        return paused;
    }

    /**
     * Get the current time according to the simulation playback thread.
     * @return current time in milliseconds from epoch.
     */
    public long getCurrentTime() {
        return currentTime;
    }
}
