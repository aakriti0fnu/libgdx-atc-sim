package com.atc.simulator.PredictionService.Engine.Algorithms;

import com.atc.simulator.PredictionService.Engine.Algorithms.OpenCL.OpenCLPredictionAlgorithm;
import com.atc.simulator.PredictionService.Engine.Algorithms.OpenCL.OpenCLUtils;
import com.atc.simulator.PredictionService.Engine.PredictionWorkItem;
import com.atc.simulator.flightdata.AircraftState;
import com.atc.simulator.flightdata.Track;
import com.atc.simulator.vectors.GeographicCoordinate;
import com.atc.simulator.vectors.SphericalVelocity;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import pythagoras.d.Vector3;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * SphericalCoordinate Tester.
 *
 * @author <Authors name>
 * @since <pre>May 27, 2016</pre>
 * @version 1.0
 */
public class TestOpenCL {

    static {

    }

    @Before
    public void before() throws Exception {

    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void printPlatformInfoTest()
    {
        OpenCLUtils.printPlatformInfo();
    }


    @Test
    public void mainOpenCLTest()
    {
        OpenCLPredictionAlgorithm oclA = new OpenCLPredictionAlgorithm();

        ArrayList<PredictionWorkItem> work = new ArrayList<PredictionWorkItem>();

        Calendar cal = Calendar.getInstance();
        for (int i=0; i < 2; i++)
        {
            Track track = new Track();
            track.add(new AircraftState(
                    "test",
                    cal,
                    new GeographicCoordinate(new Vector3(1.11+i, 1.12+i, 1.13+i)),
                    new SphericalVelocity(new Vector3(1.21+i, 1.22+i, 1.23+i)),
                    1.31+i
            ));
            track.add(new AircraftState(
                    "test",
                    cal,
                    new GeographicCoordinate(new Vector3(2.11+i, 2.12+i, 2.13+i)),
                    new SphericalVelocity(new Vector3(2.21+i, 2.22+i, 2.23+i)),
                    2.31+i
            ));
            track.add(new AircraftState(
                    "test",
                    cal,
                    new GeographicCoordinate(new Vector3(3.11+i, 3.12+i, 3.13+i)),
                    new SphericalVelocity(new Vector3(3.21+i, 3.22+i, 3.23+i)),
                    3.31+i
            ));
            track.add(new AircraftState(
                    "test",
                    cal,
                    new GeographicCoordinate(new Vector3(4.11+i, 4.12+i, 4.13+i)),
                    new SphericalVelocity(new Vector3(4.21+i, 4.22+i, 4.23+i)),
                    4.31+i
            ));

            PredictionWorkItem workItem = new PredictionWorkItem("test", track, PredictionAlgorithmType.PASSTHROUGH);

            work.add(workItem);

        }

        oclA.run(work);
//        oclA.release();
    }

}
